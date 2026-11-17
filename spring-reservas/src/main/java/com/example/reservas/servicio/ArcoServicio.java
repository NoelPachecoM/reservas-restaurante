package com.example.reservas.servicio;

import com.example.reservas.dto.ArcoPeticion;
import com.example.reservas.excepcion.ErrorApp;
import com.example.reservas.modelo.SolicitudCuenta;
import com.example.reservas.modelo.Usuario;
import com.example.reservas.repositorio.SolicitudCuentaRepositorio;
import com.example.reservas.repositorio.UsuarioRepositorio;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derechos ARCO: lo que una persona puede hacer con sus propios datos.
 *
 *   Acceso        - ver que guarda el sistema de ella (lo resuelve la pantalla "Mi cuenta").
 *   Rectificacion - corregir su nombre, su telefono y su correo.
 *   Cancelacion   - pedir que se borren sus datos personales.
 *   Oposicion     - cancelar sus reservas y dejar de recibir avisos.
 *
 * Los dos tramites que NO se pueden deshacer llevan codigo de confirmacion por correo,
 * ademas de la contrasena:
 *
 *   BORRAR LA CUENTA: un codigo al correo de la cuenta.
 *   CAMBIAR EL CORREO: un codigo al correo ACTUAL (para comprobar que lo pide el dueno)
 *   y despues otro al correo NUEVO (para comprobar que ese correo existe y es suyo).
 *   Hasta que los dos se confirman, el correo no se mueve.
 */
@Service
public class ArcoServicio {

    private static final int MINUTOS_DE_VIDA = 20;
    private static final int INTENTOS_MAXIMOS = 5;

    private final SolicitudCuentaRepositorio solicitudes;
    private final UsuarioRepositorio usuarios;
    private final CuentaServicio cuentas;
    private final Contrasenas contrasenas;
    private final ServicioCorreo correo;
    private final Config config;
    private final boolean devolverCodigo;
    private final SecureRandom aleatorio = new SecureRandom();

    public ArcoServicio(SolicitudCuentaRepositorio solicitudes, UsuarioRepositorio usuarios,
                        CuentaServicio cuentas, Contrasenas contrasenas, ServicioCorreo correo,
                        Config config,
                        @Value("${registro.devolver-codigo:false}") boolean devolverCodigo) {
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
        this.cuentas = cuentas;
        this.contrasenas = contrasenas;
        this.correo = correo;
        this.config = config;
        this.devolverCodigo = devolverCodigo;
    }

    // =====================================================================
    // Cancelacion: borrar la cuenta
    // =====================================================================

    /** Paso 1: con la contrasena correcta se manda un codigo al correo de la cuenta. */
    @Transactional
    public Map<String, Object> pedirCodigoDeBaja(Usuario u, ArcoPeticion p) {
        revisarContrasena(u, p.getContrasenaActual());
        limpiar();
        solicitudes.borrarDeUsuarioYTipo(u.getId(), SolicitudCuenta.BAJA);

        SolicitudCuenta s = nueva(u, SolicitudCuenta.BAJA);
        s.setCodigoActual(codigo());
        solicitudes.save(s);

        enviar(correoDe(u), "Codigo para eliminar tu cuenta",
                "Hola " + u.getPersona().getNombre() + ",\n\n"
                        + "Pediste eliminar tu cuenta. Tu codigo es: " + s.getCodigoActual() + "\n\n"
                        + "Al confirmarlo se borran tu nombre, tu telefono y tu correo, y se cancelan "
                        + "tus reservas que no han pasado. Esto no se puede deshacer.\n\n"
                        + "Si no fuiste tu, NO uses este codigo: tu cuenta no cambia sin el, "
                        + "pero cambia tu contrasena por si acaso.");

        Map<String, Object> r = respuesta(s, "Te mandamos un codigo a " + correoDe(u)
                + ". Escribelo para confirmar que quieres eliminar tu cuenta.");
        r.put("paso", "CODIGO");
        return r;
    }

    /** Paso 2: con la contrasena Y el codigo, la cuenta se borra de verdad. */
    @Transactional
    public Map<String, Object> confirmarBaja(Usuario u, ArcoPeticion p, String token) {
        revisarContrasena(u, p.getContrasenaActual());
        SolicitudCuenta s = buscar(u, p.getSolicitudId(), SolicitudCuenta.BAJA);
        revisarCodigo(s, s.getCodigoActual(), p.getCodigo());

        s.setUsado(true);
        solicitudes.save(s);
        solicitudes.borrarDeUsuario(u.getId());
        return cuentas.borrarCuentaConfirmada(u, token);
    }

    // =====================================================================
    // Rectificacion: cambiar el correo (dos codigos)
    // =====================================================================

    /** Paso 1: codigo al correo de HOY, para saber que el cambio lo pide el dueno. */
    @Transactional
    public Map<String, Object> pedirCambioDeCorreo(Usuario u, ArcoPeticion p) {
        revisarContrasena(u, p.getContrasenaActual());
        String nuevo = cuentas.revisarCorreo(p.getCorreoNuevo());
        if (nuevo.equalsIgnoreCase(u.getUsuario())) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Ese ya es tu correo");
        }
        if (usuarios.existsByUsuarioIgnoreCase(nuevo)) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }
        limpiar();
        solicitudes.borrarDeUsuarioYTipo(u.getId(), SolicitudCuenta.CAMBIO_CORREO);

        SolicitudCuenta s = nueva(u, SolicitudCuenta.CAMBIO_CORREO);
        s.setCorreoNuevo(nuevo);
        s.setCodigoActual(codigo());
        solicitudes.save(s);

        enviar(correoDe(u), "Confirma el cambio de tu correo",
                "Hola " + u.getPersona().getNombre() + ",\n\n"
                        + "Alguien pidio mover tu cuenta al correo " + tapar(nuevo) + ".\n"
                        + "Si fuiste tu, tu codigo es: " + s.getCodigoActual() + "\n\n"
                        + "Despues te vamos a mandar otro codigo al correo nuevo. Hasta que "
                        + "confirmes los dos, tu cuenta se queda con este correo.\n\n"
                        + "Si NO fuiste tu, ignora este mensaje y cambia tu contrasena.");

        Map<String, Object> r = respuesta(s, "Te mandamos un codigo a " + correoDe(u)
                + " (tu correo de ahora). Escribelo para seguir.");
        r.put("paso", "ACTUAL");
        r.put("correoNuevo", nuevo);
        return r;
    }

    /** Paso 2: se comprueba el codigo del correo actual y se manda otro al correo nuevo. */
    @Transactional
    public Map<String, Object> confirmarCorreoActual(Usuario u, ArcoPeticion p) {
        SolicitudCuenta s = buscar(u, p.getSolicitudId(), SolicitudCuenta.CAMBIO_CORREO);
        if (Boolean.TRUE.equals(s.getActualConfirmado())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Ese paso ya estaba confirmado");
        }
        revisarCodigo(s, s.getCodigoActual(), p.getCodigo());

        s.setActualConfirmado(true);
        s.setCodigoNuevo(codigo());
        s.setExpira(LocalDateTime.now().plusMinutes(MINUTOS_DE_VIDA));
        s.setEnviadoEn(LocalDateTime.now());
        s.setIntentos(0);
        solicitudes.save(s);

        enviar(s.getCorreoNuevo(), "Confirma tu correo nuevo",
                "Hola " + u.getPersona().getNombre() + ",\n\n"
                        + "Este correo se va a quedar como el de tu cuenta en "
                        + config.texto(Config.NOMBRE, "el restaurante") + ".\n"
                        + "Tu codigo es: " + s.getCodigoNuevo() + "\n\n"
                        + "Si no esperabas este mensaje, ignoralo: sin el codigo no se cambia nada.");

        Map<String, Object> r = respuesta(s, "Listo. Ahora te mandamos otro codigo a "
                + s.getCorreoNuevo() + ". Escribelo para terminar el cambio.");
        r.put("paso", "NUEVO");
        r.put("correoNuevo", s.getCorreoNuevo());
        if (devolverCodigo) r.put("codigo", s.getCodigoNuevo());
        return r;
    }

    /** Paso 3: con el codigo del correo nuevo, el cambio se aplica. */
    @Transactional
    public Map<String, Object> confirmarCorreoNuevo(Usuario u, ArcoPeticion p) {
        SolicitudCuenta s = buscar(u, p.getSolicitudId(), SolicitudCuenta.CAMBIO_CORREO);
        if (!Boolean.TRUE.equals(s.getActualConfirmado())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Primero confirma el codigo de tu correo de ahora");
        }
        revisarCodigo(s, s.getCodigoNuevo(), p.getCodigo());
        // Entre que empezo el tramite y lo termino, alguien mas pudo registrar ese correo
        if (usuarios.existsByUsuarioIgnoreCase(s.getCorreoNuevo())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }

        String anterior = correoDe(u);
        Map<String, Object> datos = cuentas.aplicarCorreoNuevo(u, s.getCorreoNuevo());
        s.setUsado(true);
        solicitudes.save(s);

        // Al correo viejo se le avisa, por si el cambio no lo hizo el dueno
        enviarSinFallar(anterior, "Tu correo cambio",
                "Hola " + u.getPersona().getNombre() + ",\n\n"
                        + "La cuenta que tenias en este correo ahora usa " + tapar(s.getCorreoNuevo())
                        + ".\n\nSi no fuiste tu, habla al restaurante: "
                        + config.texto(Config.TELEFONO, "") + ".");

        Map<String, Object> r = new LinkedHashMap<>(datos);
        r.put("mensaje", "Tu correo quedo cambiado. A partir de ahora entras con el nuevo.");
        return r;
    }

    // =====================================================================
    // Reenviar el codigo que toque
    // =====================================================================

    @Transactional
    public Map<String, Object> reenviar(Usuario u, ArcoPeticion p) {
        SolicitudCuenta s = buscar(u, p.getSolicitudId(), null);
        long segundos = ChronoUnit.SECONDS.between(s.getEnviadoEn(), LocalDateTime.now());
        if (segundos < 60) {
            throw new ErrorApp(HttpStatus.TOO_MANY_REQUESTS,
                    "Espera " + (60 - segundos) + " segundos para pedir otro codigo");
        }
        boolean alNuevo = SolicitudCuenta.CAMBIO_CORREO.equals(s.getTipo())
                && Boolean.TRUE.equals(s.getActualConfirmado());
        String destino = alNuevo ? s.getCorreoNuevo() : correoDe(u);
        String nuevoCodigo = codigo();
        if (alNuevo) s.setCodigoNuevo(nuevoCodigo);
        else s.setCodigoActual(nuevoCodigo);
        s.setExpira(LocalDateTime.now().plusMinutes(MINUTOS_DE_VIDA));
        s.setEnviadoEn(LocalDateTime.now());
        s.setIntentos(0);
        solicitudes.save(s);

        enviar(destino, SolicitudCuenta.BAJA.equals(s.getTipo())
                        ? "Codigo para eliminar tu cuenta" : "Codigo de confirmacion",
                "Hola " + u.getPersona().getNombre() + ",\n\nTu codigo nuevo es: " + nuevoCodigo
                        + "\n\nVence en " + MINUTOS_DE_VIDA + " minutos.");

        Map<String, Object> r = respuesta(s, "Te mandamos un codigo nuevo a " + destino + ".");
        if (devolverCodigo) r.put("codigo", nuevoCodigo);
        return r;
    }

    /** Cancelar el tramite a medias (el botón "mejor no"). */
    @Transactional
    public Map<String, Object> cancelarTramite(Usuario u) {
        solicitudes.borrarDeUsuario(u.getId());
        return Map.of("mensaje", "Se cancelo el tramite. No cambio nada en tu cuenta.");
    }

    // =====================================================================
    // Ayudas
    // =====================================================================

    private SolicitudCuenta nueva(Usuario u, String tipo) {
        SolicitudCuenta s = new SolicitudCuenta();
        s.setIdUsuario(u.getId());
        s.setTipo(tipo);
        s.setExpira(LocalDateTime.now().plusMinutes(MINUTOS_DE_VIDA));
        s.setEnviadoEn(LocalDateTime.now());
        return s;
    }

    private SolicitudCuenta buscar(Usuario u, Integer id, String tipo) {
        SolicitudCuenta s = solicitudes.findById(id == null ? -1 : id)
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND,
                        "Ese tramite ya vencio. Vuelve a empezar."));
        // Una cuenta solo puede tocar sus propios tramites
        if (!s.getIdUsuario().equals(u.getId())) {
            throw new ErrorApp(HttpStatus.FORBIDDEN, "Ese tramite no es tuyo");
        }
        if (tipo != null && !tipo.equals(s.getTipo())) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Ese tramite no corresponde");
        }
        if (Boolean.TRUE.equals(s.getUsado())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Ese tramite ya se termino");
        }
        if (s.getExpira().isBefore(LocalDateTime.now())) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "El codigo ya vencio: vuelve a empezar");
        }
        return s;
    }

    private void revisarCodigo(SolicitudCuenta s, String esperado, String escrito) {
        if (s.getIntentos() != null && s.getIntentos() >= INTENTOS_MAXIMOS) {
            throw new ErrorApp(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos: pide un codigo nuevo");
        }
        if (esperado == null || !esperado.equals(escrito == null ? "" : escrito.trim())) {
            s.setIntentos((s.getIntentos() == null ? 0 : s.getIntentos()) + 1);
            solicitudes.save(s);
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Ese codigo no es correcto");
        }
    }

    private void revisarContrasena(Usuario u, String escrita) {
        if (!contrasenas.coincide(escrita, u.getContrasenaHash())) {
            throw new ErrorApp(HttpStatus.FORBIDDEN, "Tu contrasena actual no es correcta");
        }
    }

    private String correoDe(Usuario u) {
        String c = u.getUsuario();
        if (c == null || c.isBlank()) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Tu cuenta no tiene un correo donde avisarte");
        }
        return c;
    }

    private void limpiar() {
        solicitudes.borrarVencidas(LocalDateTime.now());
    }

    private String codigo() {
        return String.format("%06d", aleatorio.nextInt(1_000_000));
    }

    private Map<String, Object> respuesta(SolicitudCuenta s, String mensaje) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("solicitudId", s.getId());
        r.put("mensaje", mensaje);
        r.put("minutos", MINUTOS_DE_VIDA);
        if (devolverCodigo) r.put("codigo", s.getCodigoActual());
        return r;
    }

    /** Muestra el correo a medias (no***@gmail.com), para no enseñarlo completo en el otro buzon. */
    private String tapar(String correoTexto) {
        int arroba = correoTexto.indexOf('@');
        if (arroba <= 2) return correoTexto;
        return correoTexto.substring(0, 2) + "***" + correoTexto.substring(arroba);
    }

    /** Si el correo no sale, el tramite no puede seguir: hay que decirlo. */
    private void enviar(String destino, String asunto, String texto) {
        String restaurante = config.texto(Config.NOMBRE, "Restaurante");
        try {
            correo.enviar(destino, restaurante + " - " + asunto, texto + "\n\n" + restaurante);
        } catch (RuntimeException e) {
            System.out.println("No se pudo mandar el codigo a " + destino + ": " + e.getMessage());
            throw new ErrorApp(HttpStatus.BAD_GATEWAY,
                    "No pudimos mandar el codigo a " + destino + ". Revisa que este bien escrito.");
        }
    }

    /** Aviso que es bueno mandar pero que no debe tumbar el tramite si falla. */
    private void enviarSinFallar(String destino, String asunto, String texto) {
        try {
            enviar(destino, asunto, texto);
        } catch (RuntimeException e) {
            System.out.println("Aviso no enviado a " + destino + ": " + e.getMessage());
        }
    }
}
