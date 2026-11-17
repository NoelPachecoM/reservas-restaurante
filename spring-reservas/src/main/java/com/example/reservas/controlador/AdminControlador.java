package com.example.reservas.controlador;

import com.example.reservas.config.SesionActual;
import com.example.reservas.excepcion.ErrorApp;
import com.example.reservas.modelo.*;
import com.example.reservas.repositorio.*;
import com.example.reservas.servicio.Config;
import com.example.reservas.servicio.Contrasenas;
import com.example.reservas.servicio.CuentaServicio;
import com.example.reservas.servicio.SesionServicio;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Panel del administrador: mesas y croquis, configuracion del restaurante, cuentas del
 * personal y de los clientes, lista de espera y los numeros del negocio.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminControlador {

    private final MesaRepositorio mesas;
    private final ZonaRepositorio zonas;
    private final UsuarioRepositorio usuarios;
    private final ReservaRepositorio reservas;
    private final PedidoRepositorio pedidos;
    private final PlatilloRepositorio platillos;
    private final ListaEsperaRepositorio espera;
    private final ParametroRepositorio parametros;
    private final Config config;
    private final Contrasenas contrasenas;
    private final CuentaServicio cuentas;
    private final SesionServicio sesiones;
    private final SecureRandom aleatorio = new SecureRandom();

    public AdminControlador(MesaRepositorio mesas, ZonaRepositorio zonas, UsuarioRepositorio usuarios,
                            ReservaRepositorio reservas, PedidoRepositorio pedidos,
                            PlatilloRepositorio platillos, ListaEsperaRepositorio espera,
                            ParametroRepositorio parametros, Config config, Contrasenas contrasenas,
                            CuentaServicio cuentas, SesionServicio sesiones) {
        this.mesas = mesas;
        this.zonas = zonas;
        this.usuarios = usuarios;
        this.reservas = reservas;
        this.pedidos = pedidos;
        this.platillos = platillos;
        this.espera = espera;
        this.parametros = parametros;
        this.config = config;
        this.contrasenas = contrasenas;
        this.cuentas = cuentas;
        this.sesiones = sesiones;
    }

    // =====================================================================
    // Mesas (el numero de mesa lo pone el administrador, no es autonumerico)
    // =====================================================================

    @GetMapping("/mesas")
    public List<Mesa> listarMesas() {
        return mesas.findAll().stream().sorted(Comparator.comparing(Mesa::getId)).toList();
    }

    @PostMapping("/mesas")
    public Mesa crearMesa(@RequestBody Mesa nueva) {
        if (nueva.getId() == null || nueva.getId() < 1) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Escribe el numero de la mesa");
        }
        if (mesas.existsById(nueva.getId())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Ya existe la mesa " + nueva.getId());
        }
        revisarMesa(nueva);
        return mesas.save(nueva);
    }

    @PutMapping("/mesas/{id}")
    public Mesa actualizarMesa(@PathVariable Integer id, @RequestBody Mesa datos) {
        if (!mesas.existsById(id)) throw new ErrorApp(HttpStatus.NOT_FOUND, "No existe esa mesa");
        datos.setId(id);
        revisarMesa(datos);
        return mesas.save(datos);
    }

    /** Guarda de golpe las posiciones despues de arrastrar las mesas en el croquis. */
    @PutMapping("/mesas/posiciones")
    public List<Mesa> guardarPosiciones(@RequestBody List<Mesa> lista) {
        List<Mesa> salida = new ArrayList<>();
        for (Mesa cambio : lista) {
            Mesa mesa = mesas.findById(cambio.getId())
                    .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "No existe la mesa " + cambio.getId()));
            mesa.setX(cambio.getX());
            mesa.setY(cambio.getY());
            if (cambio.getZona() != null) mesa.setZona(cambio.getZona());
            salida.add(mesas.save(mesa));
        }
        return salida;
    }

    @DeleteMapping("/mesas/{id}")
    public Map<String, Object> borrarMesa(@PathVariable Integer id) {
        try {
            mesas.deleteById(id);
            mesas.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ErrorApp(HttpStatus.CONFLICT,
                    "Esa mesa ya tiene reservas registradas. Desactivala en lugar de borrarla.");
        }
        return Map.of("mensaje", "Mesa eliminada");
    }

    private void revisarMesa(Mesa mesa) {
        if (mesa.getCapacidad() == null || mesa.getCapacidad() < 1 || mesa.getCapacidad() > 20) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "La capacidad de la mesa va de 1 a 20 personas");
        }
        if (mesa.getZona() != null && mesa.getZona().getId() != null) {
            Zona zona = zonas.findById(mesa.getZona().getId())
                    .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "Esa zona no existe"));
            if (!Boolean.TRUE.equals(zona.getReservable())) {
                throw new ErrorApp(HttpStatus.BAD_REQUEST,
                        "En " + zona.getNombre() + " no se pueden poner mesas (es area de servicio)");
            }
            mesa.setZona(zona);
        }
        if (mesa.getForma() == null || !List.of("REDONDA", "CUADRADA").contains(mesa.getForma())) {
            mesa.setForma("REDONDA");
        }
    }

    // =====================================================================
    // Configuracion del restaurante
    // =====================================================================

    @GetMapping("/parametros")
    public List<Parametro> listarParametros() {
        return parametros.findAll().stream().sorted(Comparator.comparing(Parametro::getId)).toList();
    }

    /** Guarda varios parametros de un jalon: {"hora_apertura":"13:00","aforo_por_franja":"90"}. */
    @PutMapping("/parametros")
    public Map<String, Object> guardarParametros(@RequestBody Map<String, String> valores) {
        valores.forEach((id, valor) -> {
            if (id == null || id.isBlank()) return;
            config.guardar(id.trim(), valor);
        });
        return config.restaurante();
    }

    /** Genera un codigo nuevo para que se registre el personal. El anterior deja de servir. */
    @PostMapping("/codigo-empleado")
    public Map<String, Object> nuevoCodigoEmpleado() {
        String letras = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("EMP-");
        for (int i = 0; i < 6; i++) sb.append(letras.charAt(aleatorio.nextInt(letras.length())));
        config.guardar(Config.CODIGO_EMPLEADO, sb.toString());
        return Map.of("codigo", sb.toString());
    }

    @GetMapping("/codigo-empleado")
    public Map<String, Object> codigoEmpleado() {
        return Map.of("codigo", config.texto(Config.CODIGO_EMPLEADO, ""));
    }

    // =====================================================================
    // Cuentas
    // =====================================================================

    @GetMapping("/usuarios")
    public List<Map<String, Object>> listarUsuarios() {
        return usuarios.findAll().stream()
                .sorted(Comparator.comparing(Usuario::getId))
                .map(u -> {
                    Map<String, Object> v = new LinkedHashMap<>(cuentas.yo(u));
                    v.put("activo", u.getActivo());
                    v.put("fechaRegistro", u.getFechaRegistro().toString());
                    v.put("reservas", reservas.findByPersona_IdOrderByFechaDescHoraDesc(
                            u.getPersona().getId()).size());
                    return v;
                }).toList();
    }

    @PostMapping("/usuarios/{id}/rol")
    public Map<String, Object> cambiarRol(HttpServletRequest request, @PathVariable Integer id,
                                          @RequestParam String valor) {
        Usuario u = buscarUsuario(id);
        if (!List.of("CLIENTE", "EMPLEADO", "ADMINISTRADOR").contains(valor)) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Ese rol no existe");
        }
        if (u.getId().equals(SesionActual.usuario(request).getId())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "No puedes cambiar tu propio rol");
        }
        u.setRol(cuentas.rol(valor));
        usuarios.save(u);
        sesiones.cerrarTodas(u.getId()); // para que el cambio de permisos aplique de inmediato
        return cuentas.yo(u);
    }

    @PostMapping("/usuarios/{id}/activo")
    public Map<String, Object> activar(HttpServletRequest request, @PathVariable Integer id,
                                       @RequestParam boolean valor) {
        Usuario u = buscarUsuario(id);
        if (u.getId().equals(SesionActual.usuario(request).getId())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "No puedes desactivar tu propia cuenta");
        }
        u.setActivo(valor);
        usuarios.save(u);
        if (!valor) sesiones.cerrarTodas(u.getId());
        return cuentas.yo(u);
    }

    /** Contrasena temporal para alguien que no puede entrar. Tendra que cambiarla al iniciar sesion. */
    @PostMapping("/usuarios/{id}/contrasena")
    public Map<String, Object> reiniciarContrasena(@PathVariable Integer id) {
        Usuario u = buscarUsuario(id);
        String temporal = "Temp" + (1000 + aleatorio.nextInt(9000)) + "x";
        u.setContrasenaHash(contrasenas.cifrar(temporal));
        u.setDebeCambiarContrasena(true);
        usuarios.save(u);
        sesiones.cerrarTodas(u.getId());
        return Map.of("contrasenaTemporal", temporal,
                "mensaje", "Dasela a la persona: al entrar el sistema le va a pedir cambiarla.");
    }

    private Usuario buscarUsuario(Integer id) {
        return usuarios.findById(id)
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "No existe ese usuario"));
    }

    // =====================================================================
    // Lista de espera
    // =====================================================================

    @GetMapping("/lista-espera")
    public List<Map<String, Object>> listaEspera() {
        return espera.findByOrderByFechaAscHoraAsc().stream().map(e -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", e.getId());
            v.put("fecha", e.getFecha().toString());
            v.put("hora", e.getHora().toString());
            v.put("personas", e.getPersonas());
            v.put("cliente", e.getPersona().getNombre());
            v.put("telefono", e.getPersona().getTelefono());
            v.put("correo", e.getPersona().getCorreo());
            v.put("estado", e.getEstado());
            return v;
        }).toList();
    }

    @DeleteMapping("/lista-espera/{id}")
    public Map<String, Object> quitarDeEspera(@PathVariable Integer id) {
        espera.deleteById(id);
        return Map.of("mensaje", "Se quito de la lista de espera");
    }

    // =====================================================================
    // Numeros del negocio
    // =====================================================================

    /** Resumen de un rango de fechas (por defecto, los ultimos 30 dias). */
    @GetMapping("/reportes")
    public Map<String, Object> reportes(@RequestParam(required = false) String desde,
                                        @RequestParam(required = false) String hasta) {
        LocalDate fin = hasta == null || hasta.isBlank() ? LocalDate.now() : LocalDate.parse(hasta);
        LocalDate ini = desde == null || desde.isBlank() ? fin.minusDays(29) : LocalDate.parse(desde);
        List<Reserva> rango = reservas.findByFechaBetween(ini, fin);

        Map<String, Integer> porEstado = new LinkedHashMap<>();
        Map<String, Integer> porDia = new TreeMap<>();
        Map<String, Integer> porHora = new TreeMap<>();
        Map<String, Integer> porZona = new LinkedHashMap<>();
        int comensales = 0;
        for (Reserva r : rango) {
            porEstado.merge(r.getEstado().getNombre(), 1, Integer::sum);
            porDia.merge(r.getFecha().toString(), 1, Integer::sum);
            porHora.merge(r.getHora().toString(), 1, Integer::sum);
            String zona = r.getMesa().getZona() == null ? "Sin zona" : r.getMesa().getZona().getNombre();
            porZona.merge(zona, 1, Integer::sum);
            if (!"CANCELADA".equals(r.getEstado().getId())) comensales += r.getPersonas();
        }
        // Platillos mas pedidos en el rango
        Map<String, Integer> masPedidos = new LinkedHashMap<>();
        double venta = 0;
        for (Pedido p : pedidos.findAll()) {
            LocalDate dia = p.getReserva().getFecha();
            if (dia.isBefore(ini) || dia.isAfter(fin)) continue;
            if ("CANCELADO".equals(p.getEstado().getId())) continue;
            for (PedidoDetalle l : p.getLineas()) {
                masPedidos.merge(l.getPlatillo().getNombre(), l.getCantidad(), Integer::sum);
                venta += l.getPrecioUnitario() * l.getCantidad();
            }
        }
        List<Map<String, Object>> top = masPedidos.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("platillo", e.getKey());
                    v.put("cantidad", e.getValue());
                    return v;
                }).toList();

        long canceladas = rango.stream().filter(r -> "CANCELADA".equals(r.getEstado().getId())).count();
        long noShow = rango.stream().filter(r -> "NO_SE_PRESENTO".equals(r.getEstado().getId())).count();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("desde", ini.toString());
        r.put("hasta", fin.toString());
        r.put("reservas", rango.size());
        r.put("comensales", comensales);
        r.put("canceladas", canceladas);
        r.put("noSePresentaron", noShow);
        r.put("porcentajeNoShow", rango.isEmpty() ? 0 : Math.round(noShow * 1000.0 / rango.size()) / 10.0);
        r.put("ventaEnPedidos", Math.round(venta * 100.0) / 100.0);
        r.put("porEstado", porEstado);
        r.put("porDia", porDia);
        r.put("porHora", porHora);
        r.put("porZona", porZona);
        r.put("platillosMasPedidos", top);
        r.put("mesasActivas", mesas.findByActivaTrue().size());
        r.put("platillosActivos", platillos.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo())).count());
        return r;
    }

    /** Todas las reservas, para la tabla del administrador. */
    @GetMapping("/reservas")
    public List<Map<String, Object>> todasLasReservas(
            @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        List<Reserva> lista = (desde == null || desde.isBlank())
                ? reservas.findAllByOrderByFechaDescHoraDesc()
                : reservas.findByFechaBetween(LocalDate.parse(desde),
                        hasta == null || hasta.isBlank() ? LocalDate.now() : LocalDate.parse(hasta));
        return lista.stream().map(r -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", r.getId());
            v.put("fecha", r.getFecha().toString());
            v.put("hora", r.getHora().toString());
            v.put("personas", r.getPersonas());
            v.put("idMesa", r.getMesa().getId());
            v.put("zona", r.getMesa().getZona() == null ? null : r.getMesa().getZona().getNombre());
            v.put("cliente", r.getPersona().getNombre());
            v.put("telefono", r.getPersona().getTelefono());
            v.put("estado", r.getEstado().getId());
            v.put("estadoNombre", r.getEstado().getNombre());
            v.put("codigo", r.getCodigo());
            v.put("ocasion", r.getOcasion());
            return v;
        }).toList();
    }
}
