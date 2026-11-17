package com.example.reservas.controlador;

import com.example.reservas.config.SesionActual;
import com.example.reservas.dto.ArcoPeticion;
import com.example.reservas.dto.CodigoPeticion;
import com.example.reservas.dto.CuentaPeticion;
import com.example.reservas.dto.LoginPeticion;
import com.example.reservas.dto.RegistroConfirmacion;
import com.example.reservas.dto.RegistroPeticion;
import com.example.reservas.modelo.Usuario;
import com.example.reservas.servicio.ArcoServicio;
import com.example.reservas.servicio.CuentaServicio;
import com.example.reservas.servicio.SesionServicio;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** Registro, inicio y cierre de sesion, mi cuenta y recuperacion de contrasena. */
@RestController
@RequestMapping("/api/auth")
public class AuthControlador {

    private final CuentaServicio cuentas;
    private final ArcoServicio arco;
    private final SesionServicio sesiones;

    public AuthControlador(CuentaServicio cuentas, ArcoServicio arco, SesionServicio sesiones) {
        this.cuentas = cuentas;
        this.arco = arco;
        this.sesiones = sesiones;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginPeticion p) {
        return cuentas.login(p);
    }

    /**
     * Paso 1 del registro: manda un codigo de 6 digitos al correo. La cuenta todavia
     * no se crea; asi se comprueba que el correo es de verdad de esa persona.
     */
    @PostMapping("/registro/solicitar")
    public Map<String, Object> solicitarRegistro(@RequestBody RegistroPeticion p) {
        return cuentas.solicitarRegistro(p, false);
    }

    /** Lo mismo, pero para el personal: ademas pide el codigo de empleado. */
    @PostMapping("/registro/solicitar-empleado")
    public Map<String, Object> solicitarRegistroEmpleado(@RequestBody RegistroPeticion p) {
        return cuentas.solicitarRegistro(p, true);
    }

    /** Vuelve a mandar el codigo (hay que esperar un minuto entre envios). */
    @PostMapping("/registro/reenviar")
    public Map<String, Object> reenviar(@RequestBody RegistroConfirmacion c) {
        return cuentas.reenviarCodigo(c.getSolicitudId());
    }

    /** Paso 2: con el codigo correcto se crea la cuenta y se inicia sesion. */
    @PostMapping("/registro/confirmar")
    public Map<String, Object> confirmarRegistro(@RequestBody RegistroConfirmacion c) {
        return cuentas.confirmarRegistro(c);
    }

    @PostMapping("/olvide/codigo")
    public Map<String, Object> pedirCodigo(@RequestBody Map<String, String> cuerpo) {
        return cuentas.pedirCodigo(cuerpo.get("correo"));
    }

    @PostMapping("/olvide/cambiar")
    public Map<String, Object> cambiarConCodigo(@RequestBody CodigoPeticion p) {
        return cuentas.cambiarConCodigo(p);
    }

    /** Datos del usuario que tiene la sesion abierta (la pagina lo llama al cargar). */
    @GetMapping("/yo")
    public Map<String, Object> yo(HttpServletRequest request) {
        return cuentas.yo(SesionActual.usuario(request));
    }

    @PostMapping("/salir")
    public Map<String, Object> salir(HttpServletRequest request) {
        sesiones.cerrar(SesionActual.token(request));
        return Map.of("mensaje", "Sesion cerrada");
    }

    @PutMapping("/mi-cuenta")
    public Map<String, Object> actualizar(HttpServletRequest request, @RequestBody CuentaPeticion p) {
        Usuario u = SesionActual.usuario(request);
        return cuentas.actualizar(u, p);
    }

    // =====================================================================
    // Derechos ARCO: los dos tramites que no se pueden deshacer.
    // Los dos piden la contrasena Y un codigo que llega al correo.
    // =====================================================================

    /** Cancelacion, paso 1: manda el codigo al correo de la cuenta. */
    @PostMapping("/mi-cuenta/eliminar/solicitar")
    public Map<String, Object> pedirCodigoDeBaja(HttpServletRequest request, @RequestBody ArcoPeticion p) {
        return arco.pedirCodigoDeBaja(SesionActual.usuario(request), p);
    }

    /** Cancelacion, paso 2: con la contrasena y el codigo, la cuenta se borra. */
    @PostMapping("/mi-cuenta/eliminar/confirmar")
    public Map<String, Object> confirmarBaja(HttpServletRequest request, @RequestBody ArcoPeticion p) {
        return arco.confirmarBaja(SesionActual.usuario(request), p, SesionActual.token(request));
    }

    /** Cambio de correo, paso 1: codigo al correo de ahora. */
    @PostMapping("/mi-cuenta/correo/solicitar")
    public Map<String, Object> pedirCambioDeCorreo(HttpServletRequest request, @RequestBody ArcoPeticion p) {
        return arco.pedirCambioDeCorreo(SesionActual.usuario(request), p);
    }

    /** Cambio de correo, paso 2: se confirma ese codigo y sale otro al correo nuevo. */
    @PostMapping("/mi-cuenta/correo/confirmar-actual")
    public Map<String, Object> confirmarCorreoActual(HttpServletRequest request, @RequestBody ArcoPeticion p) {
        return arco.confirmarCorreoActual(SesionActual.usuario(request), p);
    }

    /** Cambio de correo, paso 3: con el codigo del correo nuevo, el cambio se aplica. */
    @PostMapping("/mi-cuenta/correo/confirmar-nuevo")
    public Map<String, Object> confirmarCorreoNuevo(HttpServletRequest request, @RequestBody ArcoPeticion p) {
        return arco.confirmarCorreoNuevo(SesionActual.usuario(request), p);
    }

    /** Reenvia el codigo que toque del tramite que este abierto. */
    @PostMapping("/mi-cuenta/reenviar")
    public Map<String, Object> reenviarCodigoDeCuenta(HttpServletRequest request, @RequestBody ArcoPeticion p) {
        return arco.reenviar(SesionActual.usuario(request), p);
    }

    /** Deja el tramite sin efecto. */
    @PostMapping("/mi-cuenta/cancelar-tramite")
    public Map<String, Object> cancelarTramite(HttpServletRequest request) {
        return arco.cancelarTramite(SesionActual.usuario(request));
    }
}
