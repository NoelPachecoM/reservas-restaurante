package com.example.reservas.controlador;

import com.example.reservas.config.ProteccionApi;
import com.example.reservas.config.SesionActual;
import com.example.reservas.dto.PedidoPeticion;
import com.example.reservas.dto.ReservaPeticion;
import com.example.reservas.modelo.Usuario;
import com.example.reservas.servicio.PedidoServicio;
import com.example.reservas.servicio.ReservaServicio;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Las reservas y los pedidos del cliente que tiene la sesion abierta. */
@RestController
@RequestMapping("/api/reservas")
public class ReservaControlador {

    private final ReservaServicio servicio;
    private final PedidoServicio pedidos;

    public ReservaControlador(ReservaServicio servicio, PedidoServicio pedidos) {
        this.servicio = servicio;
        this.pedidos = pedidos;
    }

    @GetMapping
    public List<Map<String, Object>> mias(HttpServletRequest request) {
        return servicio.mias(SesionActual.usuario(request).getPersona());
    }

    @GetMapping("/{id}")
    public Map<String, Object> una(HttpServletRequest request, @PathVariable Integer id) {
        Usuario u = SesionActual.usuario(request);
        return servicio.una(id, u.getPersona(), ProteccionApi.esPersonal(u));
    }

    @PostMapping
    public Map<String, Object> crear(HttpServletRequest request, @RequestBody ReservaPeticion p,
                                     @RequestParam(required = false) String clave) {
        Usuario u = SesionActual.usuario(request);
        Map<String, Object> reserva = servicio.crear(p, u.getPersona(), null, clave(clave, u));
        // Si el cliente ya eligio platillos al reservar, se manda el pedido de una vez
        if (p.getLineas() != null && !p.getLineas().isEmpty()) {
            PedidoPeticion pedido = new PedidoPeticion();
            pedido.setIdReserva((Integer) reserva.get("id"));
            pedido.setLineas(p.getLineas());
            pedidos.crear(pedido, u.getPersona(), false);
        }
        return reserva;
    }

    @PutMapping("/{id}")
    public Map<String, Object> actualizar(HttpServletRequest request, @PathVariable Integer id,
                                          @RequestBody ReservaPeticion p,
                                          @RequestParam(required = false) String clave) {
        Usuario u = SesionActual.usuario(request);
        return servicio.actualizar(id, p, u.getPersona(), ProteccionApi.esPersonal(u), clave(clave, u));
    }

    @PostMapping("/{id}/cancelar")
    public Map<String, Object> cancelar(HttpServletRequest request, @PathVariable Integer id) {
        Usuario u = SesionActual.usuario(request);
        return servicio.cancelar(id, u.getPersona(), ProteccionApi.esPersonal(u));
    }

    /** Anotarse en la lista de espera cuando no quedan mesas a esa hora. */
    @PostMapping("/lista-espera")
    public Map<String, Object> listaEspera(HttpServletRequest request, @RequestBody ReservaPeticion p) {
        return servicio.anotarEnEspera(SesionActual.usuario(request).getPersona(), p);
    }

    // ---------------- Pedidos de mi reserva ----------------

    @GetMapping("/{id}/pedidos")
    public Map<String, Object> pedidosDe(HttpServletRequest request, @PathVariable Integer id) {
        Usuario u = SesionActual.usuario(request);
        return pedidos.deReserva(id, u.getPersona(), ProteccionApi.esPersonal(u));
    }

    @PostMapping("/{id}/pedidos")
    public Map<String, Object> pedir(HttpServletRequest request, @PathVariable Integer id,
                                     @RequestBody PedidoPeticion p) {
        Usuario u = SesionActual.usuario(request);
        p.setIdReserva(id);
        return pedidos.crear(p, u.getPersona(), ProteccionApi.esPersonal(u));
    }

    @PutMapping("/pedidos/{idPedido}")
    public Map<String, Object> cambiarPedido(HttpServletRequest request, @PathVariable Integer idPedido,
                                             @RequestBody PedidoPeticion p) {
        Usuario u = SesionActual.usuario(request);
        return pedidos.actualizar(idPedido, p, u.getPersona(), ProteccionApi.esPersonal(u));
    }

    @DeleteMapping("/pedidos/{idPedido}")
    public Map<String, Object> borrarPedido(HttpServletRequest request, @PathVariable Integer idPedido) {
        Usuario u = SesionActual.usuario(request);
        pedidos.borrar(idPedido, u.getPersona(), ProteccionApi.esPersonal(u));
        return Map.of("mensaje", "Pedido eliminado");
    }

    /** La clave con la que se aparto la mesa. Si la pagina no la manda, se usa la del usuario. */
    private String clave(String valor, Usuario u) {
        if (valor != null && !valor.isBlank()) return valor.length() > 60 ? valor.substring(0, 60) : valor.trim();
        return "u:" + u.getId();
    }
}
