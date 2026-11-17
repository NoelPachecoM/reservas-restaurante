package com.example.reservas.controlador;

import com.example.reservas.config.SesionActual;
import com.example.reservas.dto.PedidoPeticion;
import com.example.reservas.dto.ReservaPeticion;
import com.example.reservas.modelo.Persona;
import com.example.reservas.modelo.Usuario;
import com.example.reservas.servicio.PedidoServicio;
import com.example.reservas.servicio.ReservaServicio;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * Lo que usa el personal durante el turno: la agenda del dia, el croquis en vivo,
 * marcar llegadas y salidas, mover de mesa, levantar pedidos y la pantalla de cocina.
 */
@RestController
@RequestMapping("/api/piso")
public class PisoControlador {

    private final ReservaServicio servicio;
    private final PedidoServicio pedidos;

    public PisoControlador(ReservaServicio servicio, PedidoServicio pedidos) {
        this.servicio = servicio;
        this.pedidos = pedidos;
    }

    /** Agenda de un dia (por defecto hoy), con todas las reservas en orden de hora. */
    @GetMapping("/agenda")
    public List<Map<String, Object>> agenda(@RequestParam(required = false) String fecha) {
        LocalDate dia = fecha == null || fecha.isBlank() ? LocalDate.now() : servicio.fecha(fecha);
        return servicio.agenda(dia);
    }

    /** Croquis en vivo: aqui si se ve quien ocupa cada mesa. */
    @GetMapping("/croquis")
    public List<Map<String, Object>> croquis(@RequestParam(required = false) String fecha,
                                             @RequestParam(required = false) String hora) {
        LocalDate dia = fecha == null || fecha.isBlank() ? LocalDate.now() : servicio.fecha(fecha);
        LocalTime momento = hora == null || hora.isBlank() ? redondear() : servicio.hora(hora);
        return servicio.croquis(dia, momento, "piso", null);
    }

    /** La hora actual llevada a la franja mas cercana, para ver el croquis de este momento. */
    private LocalTime redondear() {
        LocalTime ahora = LocalTime.now();
        List<LocalTime> franjas = servicio.franjas();
        LocalTime elegida = franjas.isEmpty() ? ahora : franjas.get(0);
        for (LocalTime f : franjas) {
            if (!f.isAfter(ahora)) elegida = f;
        }
        return elegida;
    }

    @PostMapping("/reservas/{id}/estado")
    public Map<String, Object> estado(@PathVariable Integer id, @RequestParam String valor) {
        return servicio.cambiarEstado(id, valor);
    }

    @PostMapping("/reservas/{id}/mover")
    public Map<String, Object> mover(@PathVariable Integer id, @RequestParam Integer idMesa) {
        return servicio.mover(id, idMesa);
    }

    /** Reserva que el personal levanta por telefono o para alguien que llego sin reservar. */
    @PostMapping("/reservas")
    public Map<String, Object> crear(HttpServletRequest request, @RequestBody ReservaPeticion p) {
        Usuario empleado = SesionActual.usuario(request);
        Persona persona = servicio.personaPara(p.getNombre(), p.getTelefono(), p.getCorreo());
        return servicio.crear(p, persona, empleado, "piso");
    }

    @GetMapping("/reservas/{id}/pedidos")
    public Map<String, Object> pedidosDe(@PathVariable Integer id) {
        return pedidos.deReserva(id, null, true);
    }

    @PostMapping("/reservas/{id}/pedidos")
    public Map<String, Object> pedir(@PathVariable Integer id, @RequestBody PedidoPeticion p) {
        p.setIdReserva(id);
        return pedidos.crear(p, null, true);
    }

    @PutMapping("/pedidos/{idPedido}")
    public Map<String, Object> cambiarPedido(@PathVariable Integer idPedido, @RequestBody PedidoPeticion p) {
        return pedidos.actualizar(idPedido, p, null, true);
    }

    @PostMapping("/pedidos/{idPedido}/estado")
    public Map<String, Object> estadoPedido(@PathVariable Integer idPedido, @RequestParam String valor) {
        return pedidos.cambiarEstado(idPedido, valor);
    }

    @DeleteMapping("/pedidos/{idPedido}")
    public Map<String, Object> borrarPedido(@PathVariable Integer idPedido) {
        pedidos.borrar(idPedido, null, true);
        return Map.of("mensaje", "Pedido eliminado");
    }

    /** Pantalla de cocina: lo que falta preparar. */
    @GetMapping("/cocina")
    public List<Map<String, Object>> cocina() {
        return pedidos.cocina();
    }

    /** Buscar una reserva con el codigo que trae el cliente. */
    @GetMapping("/buscar")
    public Map<String, Object> buscar(@RequestParam String codigo) {
        return servicio.porCodigo(codigo);
    }
}
