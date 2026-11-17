package com.example.reservas.servicio;

import com.example.reservas.dto.LineaPeticion;
import com.example.reservas.dto.PedidoPeticion;
import com.example.reservas.excepcion.ErrorApp;
import com.example.reservas.modelo.*;
import com.example.reservas.repositorio.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedidos de la mesa. El precio de cada platillo se copia al pedir, para que la cuenta
 * de una reserva vieja no cambie si despues se sube el precio en la carta.
 */
@Service
public class PedidoServicio {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String PREPARANDO = "PREPARANDO";
    public static final String SERVIDO = "SERVIDO";
    public static final String CANCELADO = "CANCELADO";

    private final PedidoRepositorio pedidos;
    private final PlatilloRepositorio platillos;
    private final ReservaRepositorio reservas;
    private final EstadoPedidoRepositorio estados;

    public PedidoServicio(PedidoRepositorio pedidos, PlatilloRepositorio platillos,
                          ReservaRepositorio reservas, EstadoPedidoRepositorio estados) {
        this.pedidos = pedidos;
        this.platillos = platillos;
        this.reservas = reservas;
        this.estados = estados;
    }

    @Transactional
    public Map<String, Object> crear(PedidoPeticion p, Persona quien, boolean esPersonal) {
        Reserva reserva = reservas.findById(p.getIdReserva())
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "No existe esa reserva"));
        if (!esPersonal && (quien == null || !reserva.getPersona().getId().equals(quien.getId()))) {
            throw new ErrorApp(HttpStatus.FORBIDDEN, "Esa reserva no es tuya");
        }
        // Regla original de la base de datos: solo se piden platillos sobre una reserva activa
        String estadoReserva = reserva.getEstado().getId();
        if (!ReservaServicio.CONFIRMADA.equals(estadoReserva) && !ReservaServicio.EN_MESA.equals(estadoReserva)) {
            throw new ErrorApp(HttpStatus.CONFLICT,
                    "Solo se puede pedir sobre una reserva confirmada o que ya esta en mesa");
        }
        Pedido pedido = new Pedido();
        pedido.setReserva(reserva);
        pedido.setEstado(estado(PENDIENTE));
        pedido.setLineas(lineas(p.getLineas()));
        pedidos.save(pedido);
        return vista(pedido);
    }

    /** Cambiar los platillos de un pedido. Ya no se puede si la cocina lo esta preparando. */
    @Transactional
    public Map<String, Object> actualizar(Integer id, PedidoPeticion p, Persona quien, boolean esPersonal) {
        Pedido pedido = buscar(id, quien, esPersonal);
        if (!PENDIENTE.equals(pedido.getEstado().getId()) && !esPersonal) {
            throw new ErrorApp(HttpStatus.CONFLICT, "La cocina ya tomo este pedido: avisale a tu mesero");
        }
        pedido.getLineas().clear();
        pedido.getLineas().addAll(lineas(p.getLineas()));
        pedidos.save(pedido);
        return vista(pedido);
    }

    @Transactional
    public Map<String, Object> cambiarEstado(Integer id, String nuevo) {
        Pedido pedido = pedidos.findById(id)
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "No existe ese pedido"));
        if (!List.of(PENDIENTE, PREPARANDO, SERVIDO, CANCELADO).contains(nuevo)) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Estado de pedido no valido");
        }
        pedido.setEstado(estado(nuevo));
        pedidos.save(pedido);
        return vista(pedido);
    }

    @Transactional
    public void borrar(Integer id, Persona quien, boolean esPersonal) {
        Pedido pedido = buscar(id, quien, esPersonal);
        if (!PENDIENTE.equals(pedido.getEstado().getId()) && !esPersonal) {
            throw new ErrorApp(HttpStatus.CONFLICT, "La cocina ya tomo este pedido: avisale a tu mesero");
        }
        pedidos.delete(pedido);
    }

    /** Pedidos de una reserva, con la cuenta sumada. */
    @Transactional(readOnly = true)
    public Map<String, Object> deReserva(Integer idReserva, Persona quien, boolean esPersonal) {
        Reserva reserva = reservas.findById(idReserva)
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "No existe esa reserva"));
        if (!esPersonal && (quien == null || !reserva.getPersona().getId().equals(quien.getId()))) {
            throw new ErrorApp(HttpStatus.FORBIDDEN, "Esa reserva no es tuya");
        }
        List<Pedido> lista = pedidos.findByReserva_IdOrderByIdAsc(idReserva);
        double total = lista.stream()
                .filter(x -> !CANCELADO.equals(x.getEstado().getId()))
                .mapToDouble(this::totalDe).sum();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("idReserva", idReserva);
        v.put("pedidos", lista.stream().map(this::vista).toList());
        v.put("total", redondear(total));
        return v;
    }

    /** Lo que la cocina tiene que preparar ahora. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> cocina() {
        return pedidos.findByEstado_IdInOrderByIdAsc(List.of(PENDIENTE, PREPARANDO)).stream()
                .map(this::vista).toList();
    }

    // =====================================================================

    private List<PedidoDetalle> lineas(List<LineaPeticion> peticion) {
        if (peticion == null || peticion.isEmpty()) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Agrega por lo menos un platillo");
        }
        List<PedidoDetalle> salida = new ArrayList<>();
        for (LineaPeticion l : peticion) {
            if (l.getIdPlatillo() == null) continue;
            int cantidad = l.getCantidad() == null ? 1 : l.getCantidad();
            if (cantidad < 1 || cantidad > 50) {
                throw new ErrorApp(HttpStatus.BAD_REQUEST, "La cantidad de cada platillo debe ir de 1 a 50");
            }
            Platillo platillo = platillos.findById(l.getIdPlatillo())
                    .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "Ese platillo no existe"));
            if (!Boolean.TRUE.equals(platillo.getActivo())) {
                throw new ErrorApp(HttpStatus.CONFLICT, "Hoy no hay " + platillo.getNombre());
            }
            PedidoDetalle d = new PedidoDetalle();
            d.setPlatillo(platillo);
            d.setCantidad(cantidad);
            d.setPrecioUnitario(platillo.getPrecio());
            d.setNotas(l.getNotas() == null || l.getNotas().isBlank() ? null : l.getNotas().trim());
            salida.add(d);
        }
        if (salida.isEmpty()) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Agrega por lo menos un platillo");
        }
        return salida;
    }

    private Pedido buscar(Integer id, Persona quien, boolean esPersonal) {
        Pedido pedido = pedidos.findById(id)
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "No existe ese pedido"));
        if (!esPersonal && (quien == null || !pedido.getReserva().getPersona().getId().equals(quien.getId()))) {
            throw new ErrorApp(HttpStatus.FORBIDDEN, "Ese pedido no es tuyo");
        }
        return pedido;
    }

    private EstadoPedido estado(String id) {
        return estados.findById(id)
                .orElseThrow(() -> new ErrorApp(HttpStatus.SERVICE_UNAVAILABLE, "Falta el estado " + id));
    }

    private double totalDe(Pedido pedido) {
        return pedido.getLineas().stream()
                .mapToDouble(l -> l.getPrecioUnitario() * l.getCantidad()).sum();
    }

    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    public Map<String, Object> vista(Pedido pedido) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", pedido.getId());
        v.put("idReserva", pedido.getReserva().getId());
        v.put("idMesa", pedido.getReserva().getMesa().getId());
        v.put("cliente", pedido.getReserva().getPersona().getNombre());
        v.put("estado", pedido.getEstado().getId());
        v.put("estadoNombre", pedido.getEstado().getNombre());
        v.put("fecha", pedido.getFechaRegistro().toString());
        v.put("total", redondear(totalDe(pedido)));
        List<Map<String, Object>> lineas = new ArrayList<>();
        for (PedidoDetalle l : pedido.getLineas()) {
            Map<String, Object> ln = new LinkedHashMap<>();
            ln.put("id", l.getId());
            ln.put("idPlatillo", l.getPlatillo().getId());
            ln.put("platillo", l.getPlatillo().getNombre());
            ln.put("categoria", l.getPlatillo().getCategoria().getNombre());
            ln.put("cantidad", l.getCantidad());
            ln.put("precioUnitario", l.getPrecioUnitario());
            ln.put("importe", redondear(l.getPrecioUnitario() * l.getCantidad()));
            ln.put("notas", l.getNotas());
            lineas.add(ln);
        }
        v.put("lineas", lineas);
        return v;
    }
}
