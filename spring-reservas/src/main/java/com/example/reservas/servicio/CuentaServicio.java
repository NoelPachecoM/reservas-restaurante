package com.example.reservas.servicio;

import com.example.reservas.dto.CodigoPeticion;
import com.example.reservas.dto.CuentaPeticion;
import com.example.reservas.dto.LoginPeticion;
import com.example.reservas.dto.RegistroConfirmacion;
import com.example.reservas.dto.RegistroPeticion;
import com.example.reservas.excepcion.ErrorApp;
import com.example.reservas.modelo.*;
import com.example.reservas.repositorio.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuentas: registro, inicio de sesion, cambios en la propia cuenta, recuperacion de
 * contrasena y baja de la cuenta. El nombre de usuario es el correo.
 */
@Service
public class CuentaServicio {

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]{2,}$");
    private static final Pattern SOLO_DIEZ_DIGITOS = Pattern.compile("^\\d{10}$");

    private final UsuarioRepositorio usuarios;
    private final PersonaRepositorio personas;
    private final RolRepositorio roles;
    private final OrigenCuentaRepositorio origenes;
    private final ReservaRepositorio reservas;
    private final EstadoReservaRepositorio estadosReserva;
    private final CodigoRecuperacionRepositorio codigos;
    private final SolicitudRegistroRepositorio solicitudes;
    private final Contrasenas contrasenas;
    private final SesionServicio sesiones;
    private final ServicioCorreo correo;
    private final Config config;
    /** Solo para pruebas: si esta en true, la API regresa el codigo en lugar de solo mandarlo. */
    private final boolean devolverCodigo;
    private final SecureRandom aleatorio = new SecureRandom();

    public CuentaServicio(UsuarioRepositorio usuarios, PersonaRepositorio personas, RolRepositorio roles,
                          OrigenCuentaRepositorio origenes, ReservaRepositorio reservas,
                          EstadoReservaRepositorio estadosReserva,
                          CodigoRecuperacionRepositorio codigos, SolicitudRegistroRepositorio solicitudes,
                          Contrasenas contrasenas, SesionServicio sesiones, ServicioCorreo correo,
                          Config config,
                          @Value("${registro.devolver-codigo:false}") boolean devolverCodigo) {
        this.usuarios = usuarios;
        this.personas = personas;
        this.roles = roles;
        this.origenes = origenes;
        this.reservas = reservas;
        this.estadosReserva = estadosReserva;
        this.codigos = codigos;
        this.solicitudes = solicitudes;
        this.contrasenas = contrasenas;
        this.sesiones = sesiones;
        this.correo = correo;
        this.config = config;
        this.devolverCodigo = devolverCodigo;
    }

    // =====================================================================
    // Registro e inicio de sesion
    // =====================================================================

    /**
     * PASO 1 del registro. Revisa los datos, manda un codigo de 6 digitos al correo y
     * guarda la solicitud. La cuenta TODAVIA NO SE CREA: asi nadie se registra con un
     * correo que no es suyo (ni con uno inventado, porque entonces nunca llega el codigo).
     */
    @Transactional
    public Map<String, Object> solicitarRegistro(RegistroPeticion p, boolean comoEmpleado) {
        solicitudes.borrarVencidas(LocalDateTime.now());

        String correoTexto = limpiarCorreo(p.getCorreo());
        revisarContrasena(p.getContrasena());
        if (p.getNombre() == null || p.getNombre().trim().length() < 3) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Escribe tu nombre completo");
        }
        String telefono = limpiarTelefono(p.getTelefono(), true);
        if (usuarios.existsByUsuarioIgnoreCase(correoTexto)) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }
        String rolPedido = "CLIENTE";
        if (comoEmpleado) {
            String esperado = config.texto(Config.CODIGO_EMPLEADO, "");
            if (esperado.isBlank() || !esperado.trim().equalsIgnoreCase(
                    p.getCodigoEmpleado() == null ? "" : p.getCodigoEmpleado().trim())) {
                throw new ErrorApp(HttpStatus.FORBIDDEN, "El codigo de empleado no es correcto");
            }
            rolPedido = "EMPLEADO";
        }

        // Si esa persona ya habia pedido un codigo antes, se queda solo el nuevo
        solicitudes.borrarDeCorreo(correoTexto);

        SolicitudRegistro s = new SolicitudRegistro();
        s.setNombre(p.getNombre().trim());
        s.setTelefono(telefono);
        s.setCorreo(correoTexto);
        s.setContrasenaHash(contrasenas.cifrar(p.getContrasena()));
        s.setRol(rolPedido);
        s.setCodigo(codigoDeSeisDigitos());
        s.setExpira(LocalDateTime.now().plusMinutes(20));
        s.setEnviadoEn(LocalDateTime.now());
        solicitudes.save(s);

        mandarCodigoDeRegistro(s);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("solicitudId", s.getId());
        r.put("correo", correoTexto);
        r.put("mensaje", "Te mandamos un codigo de 6 digitos a " + correoTexto
                + ". Escribelo para terminar de crear tu cuenta.");
        if (devolverCodigo) r.put("codigo", s.getCodigo()); // solo en pruebas
        return r;
    }

    /** Vuelve a mandar el mismo codigo. Hay que esperar un minuto entre envios. */
    @Transactional
    public Map<String, Object> reenviarCodigo(Integer solicitudId) {
        SolicitudRegistro s = solicitudes.findById(solicitudId)
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND,
                        "Ese registro ya vencio. Vuelve a llenar tus datos."));
        long segundos = ChronoUnit.SECONDS.between(s.getEnviadoEn(), LocalDateTime.now());
        if (segundos < 60) {
            throw new ErrorApp(HttpStatus.TOO_MANY_REQUESTS,
                    "Espera " + (60 - segundos) + " segundos para pedir otro codigo");
        }
        s.setCodigo(codigoDeSeisDigitos());
        s.setExpira(LocalDateTime.now().plusMinutes(20));
        s.setEnviadoEn(LocalDateTime.now());
        s.setIntentos(0);
        solicitudes.save(s);
        mandarCodigoDeRegistro(s);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("solicitudId", s.getId());
        r.put("mensaje", "Te mandamos un codigo nuevo a " + s.getCorreo() + ".");
        if (devolverCodigo) r.put("codigo", s.getCodigo());
        return r;
    }

    /**
     * PASO 2 del registro. Con el codigo correcto se crea de verdad la cuenta y se
     * inicia sesion, para que la persona siga con lo que estaba haciendo.
     */
    @Transactional
    public Map<String, Object> confirmarRegistro(RegistroConfirmacion c) {
        SolicitudRegistro s = solicitudes.findById(c.getSolicitudId() == null ? -1 : c.getSolicitudId())
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND,
                        "Ese registro ya vencio. Vuelve a llenar tus datos."));
        if (Boolean.TRUE.equals(s.getUsado())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Ese codigo ya se uso");
        }
        if (s.getExpira().isBefore(LocalDateTime.now())) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "El codigo ya vencio: pide uno nuevo");
        }
        if (s.getIntentos() != null && s.getIntentos() >= 5) {
            throw new ErrorApp(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos: pide un codigo nuevo");
        }
        if (!s.getCodigo().equals(c.getCodigo() == null ? "" : c.getCodigo().trim())) {
            s.setIntentos((s.getIntentos() == null ? 0 : s.getIntentos()) + 1);
            solicitudes.save(s);
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Ese codigo no es correcto");
        }
        // Entre que pidio el codigo y lo escribio, alguien mas pudo tomar el correo
        if (usuarios.existsByUsuarioIgnoreCase(s.getCorreo())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }

        Persona persona = new Persona();
        persona.setNombre(s.getNombre());
        persona.setTelefono(s.getTelefono());
        persona.setCorreo(s.getCorreo());
        personas.save(persona);

        Usuario u = new Usuario();
        u.setPersona(persona);
        u.setUsuario(s.getCorreo());
        u.setContrasenaHash(s.getContrasenaHash());   // ya venia cifrada
        u.setRol(rol(s.getRol()));
        u.setOrigen(origen("LOCAL"));
        u.setActivo(true);
        u.setDebeCambiarContrasena(false);
        usuarios.save(u);

        s.setUsado(true);
        solicitudes.save(s);

        try {
            correo.enviar(s.getCorreo(), config.texto(Config.NOMBRE, "Restaurante") + " - Cuenta creada",
                    "Hola " + persona.getNombre() + ",\n\nTu correo quedo verificado y tu cuenta lista. "
                            + "Ya puedes reservar tu mesa y elegir donde sentarte en el croquis.\n\n"
                            + config.texto(Config.NOMBRE, "Restaurante"));
        } catch (RuntimeException e) {
            System.out.println("No se pudo enviar el correo de bienvenida: " + e.getMessage());
        }
        return conSesion(u);
    }

    /** Si el correo no sale, la cuenta no se puede crear: hay que avisarlo. */
    private void mandarCodigoDeRegistro(SolicitudRegistro s) {
        String restaurante = config.texto(Config.NOMBRE, "Restaurante");
        try {
            correo.enviar(s.getCorreo(), restaurante + " - Tu codigo de verificacion",
                    "Hola " + s.getNombre() + ",\n\n"
                            + "Tu codigo para crear la cuenta es: " + s.getCodigo() + "\n\n"
                            + "Vence en 20 minutos. Si no fuiste tu, ignora este mensaje: "
                            + "sin el codigo no se crea ninguna cuenta.\n\n" + restaurante);
        } catch (RuntimeException e) {
            System.out.println("No se pudo enviar el codigo de registro: " + e.getMessage());
            throw new ErrorApp(HttpStatus.BAD_GATEWAY,
                    "No pudimos mandar el codigo a ese correo. Revisa que este bien escrito.");
        }
    }

    private String codigoDeSeisDigitos() {
        return String.format("%06d", aleatorio.nextInt(1_000_000));
    }

    @Transactional
    public Map<String, Object> login(LoginPeticion p) {
        String usuarioTexto = p.getUsuario() == null ? "" : p.getUsuario().trim();
        Usuario u = usuarios.findByUsuarioIgnoreCase(usuarioTexto).orElse(null);
        // El mismo mensaje exista o no la cuenta: asi no se averigua quien esta registrado
        if (u == null || !contrasenas.coincide(p.getContrasena(), u.getContrasenaHash())) {
            throw new ErrorApp(HttpStatus.UNAUTHORIZED, "Correo o contrasena incorrectos");
        }
        if (Boolean.FALSE.equals(u.getActivo())) {
            throw new ErrorApp(HttpStatus.FORBIDDEN, "Esta cuenta esta desactivada");
        }
        return conSesion(u);
    }

    public Map<String, Object> conSesion(Usuario u) {
        Map<String, Object> r = new LinkedHashMap<>(yo(u));
        r.put("token", sesiones.crear(u));
        return r;
    }

    /** Lo que la pagina necesita saber del usuario con la sesion abierta. */
    public Map<String, Object> yo(Usuario u) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", u.getId());
        r.put("idPersona", u.getPersona().getId());
        r.put("nombre", u.getPersona().getNombre());
        r.put("correo", u.getUsuario());
        r.put("telefono", u.getPersona().getTelefono());
        r.put("rol", u.getRol().getId());
        r.put("rolNombre", u.getRol().getNombre());
        r.put("esAdmin", "ADMINISTRADOR".equals(u.getRol().getId()));
        r.put("esPersonal", "ADMINISTRADOR".equals(u.getRol().getId()) || "EMPLEADO".equals(u.getRol().getId()));
        r.put("debeCambiarContrasena", Boolean.TRUE.equals(u.getDebeCambiarContrasena()));
        return r;
    }

    // =====================================================================
    // Mi cuenta
    // =====================================================================

    /**
     * Cambiar nombre, telefono o contrasena. Siempre se confirma con la contrasena actual.
     *
     * El CORREO no se cambia aqui: necesita confirmarse con un codigo en el correo de hoy
     * y otro en el nuevo, y eso lo lleva ArcoServicio.
     */
    @Transactional
    public Map<String, Object> actualizar(Usuario u, CuentaPeticion p) {
        if (!contrasenas.coincide(p.getContrasenaActual(), u.getContrasenaHash())) {
            throw new ErrorApp(HttpStatus.FORBIDDEN, "Tu contrasena actual no es correcta");
        }
        Persona persona = u.getPersona();
        if (p.getNombre() != null && !p.getNombre().isBlank()) {
            if (p.getNombre().trim().length() < 3) {
                throw new ErrorApp(HttpStatus.BAD_REQUEST, "Escribe tu nombre completo");
            }
            persona.setNombre(p.getNombre().trim());
        }
        if (p.getTelefono() != null && !p.getTelefono().isBlank()) {
            persona.setTelefono(limpiarTelefono(p.getTelefono(), true));
        }
        if (p.getCorreo() != null && !p.getCorreo().isBlank()
                && !p.getCorreo().trim().equalsIgnoreCase(u.getUsuario())) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST,
                    "Para cambiar tu correo hay que confirmarlo con los codigos que te mandamos");
        }
        if (p.getContrasenaNueva() != null && !p.getContrasenaNueva().isBlank()) {
            revisarContrasena(p.getContrasenaNueva());
            u.setContrasenaHash(contrasenas.cifrar(p.getContrasenaNueva()));
            u.setDebeCambiarContrasena(false);
        }
        personas.save(persona);
        usuarios.save(u);
        return yo(u);
    }

    /**
     * Baja de la cuenta, YA CONFIRMADA (ArcoServicio revisa antes la contrasena y el codigo
     * que llego al correo). Se borra la cuenta y sus datos personales, pero las reservas
     * pasadas quedan con una persona anonima: son el historial de ventas del restaurante.
     */
    @Transactional
    public Map<String, Object> borrarCuentaConfirmada(Usuario u, String token) {
        if ("ADMINISTRADOR".equals(u.getRol().getId())) {
            throw new ErrorApp(HttpStatus.CONFLICT,
                    "La cuenta del administrador no se puede borrar desde aqui");
        }
        String aviso = u.getPersona().getCorreo();
        String nombre = u.getPersona().getNombre();
        // Cancela las reservas que todavia no han pasado, para liberar esas mesas
        EstadoReserva cancelada = estadosReserva.findById("CANCELADA")
                .orElseThrow(() -> new ErrorApp(HttpStatus.SERVICE_UNAVAILABLE, "Falta el estado CANCELADA"));
        reservas.findByPersona_IdOrderByFechaDescHoraDesc(u.getPersona().getId()).stream()
                .filter(r -> "CONFIRMADA".equals(r.getEstado().getId()))
                .filter(r -> r.getFecha().atTime(r.getHora()).isAfter(LocalDateTime.now()))
                .forEach(r -> {
                    r.setEstado(cancelada);
                    reservas.save(r);
                });
        // Anonimiza a la persona: se quedan las reservas, se van los datos personales
        Persona persona = u.getPersona();
        persona.setNombre("Cliente dado de baja");
        persona.setTelefono(null);
        persona.setCorreo(null);
        personas.save(persona);
        sesiones.cerrarTodas(u.getId());
        usuarios.delete(u);
        sesiones.cerrar(token);
        if (aviso != null && !aviso.isBlank()) {
            try {
                correo.enviar(aviso, config.texto(Config.NOMBRE, "Restaurante") + " - Cuenta eliminada",
                        "Hola " + nombre + ",\n\nTu cuenta fue eliminada y tus datos personales se borraron. "
                                + "Puedes volver a registrarte con este mismo correo cuando quieras.");
            } catch (RuntimeException e) {
                System.out.println("No se pudo avisar de la baja: " + e.getMessage());
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mensaje", "Tu cuenta y tus datos personales se eliminaron.");
        return r;
    }

    /** Deja el correo nuevo en la cuenta. Solo se llama cuando los dos codigos ya se confirmaron. */
    @Transactional
    public Map<String, Object> aplicarCorreoNuevo(Usuario u, String correoNuevo) {
        String limpio = limpiarCorreo(correoNuevo);
        if (usuarios.existsByUsuarioIgnoreCase(limpio) && !limpio.equalsIgnoreCase(u.getUsuario())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }
        Persona persona = u.getPersona();
        persona.setCorreo(limpio);
        personas.save(persona);
        u.setUsuario(limpio);
        usuarios.save(u);
        return yo(u);
    }

    /** Revisa que un correo este bien escrito y lo regresa en minusculas. */
    public String revisarCorreo(String valor) {
        return limpiarCorreo(valor);
    }

    // =====================================================================
    // Olvide mi contrasena
    // =====================================================================

    @Transactional
    public Map<String, Object> pedirCodigo(String correoTexto) {
        Map<String, Object> r = new LinkedHashMap<>();
        // Siempre la misma respuesta, exista o no la cuenta
        r.put("mensaje", "Si ese correo tiene cuenta, te mandamos un codigo de 6 digitos.");
        Usuario u = usuarios.findByUsuarioIgnoreCase(correoTexto == null ? "" : correoTexto.trim()).orElse(null);
        if (u == null || Boolean.FALSE.equals(u.getActivo())) return r;

        String codigo = codigoDeSeisDigitos();
        CodigoRecuperacion c = new CodigoRecuperacion();
        c.setIdUsuario(u.getId());
        c.setCodigo(codigo);
        c.setExpira(LocalDateTime.now().plusMinutes(20));
        codigos.save(c);
        try {
            correo.enviar(u.getUsuario(), config.texto(Config.NOMBRE, "Restaurante") + " - Codigo de recuperacion",
                    "Tu codigo es: " + codigo + "\n\nVence en 20 minutos. "
                            + "Si no lo pediste, ignora este mensaje.");
        } catch (RuntimeException e) {
            System.out.println("No se pudo enviar el codigo: " + e.getMessage());
        }
        return r;
    }

    @Transactional
    public Map<String, Object> cambiarConCodigo(CodigoPeticion p) {
        Usuario u = usuarios.findByUsuarioIgnoreCase(p.getCorreo() == null ? "" : p.getCorreo().trim())
                .orElseThrow(() -> new ErrorApp(HttpStatus.BAD_REQUEST, "El codigo no es valido o ya vencio"));
        CodigoRecuperacion c = codigos.findFirstByIdUsuarioAndUsadoFalseOrderByIdDesc(u.getId())
                .orElseThrow(() -> new ErrorApp(HttpStatus.BAD_REQUEST, "El codigo no es valido o ya vencio"));
        if (c.getExpira().isBefore(LocalDateTime.now())) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "El codigo ya vencio: pide uno nuevo");
        }
        if (c.getIntentos() != null && c.getIntentos() >= 5) {
            throw new ErrorApp(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos: pide un codigo nuevo");
        }
        if (!c.getCodigo().equals(p.getCodigo() == null ? "" : p.getCodigo().trim())) {
            c.setIntentos((c.getIntentos() == null ? 0 : c.getIntentos()) + 1);
            codigos.save(c);
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "El codigo no es valido o ya vencio");
        }
        revisarContrasena(p.getContrasenaNueva());
        u.setContrasenaHash(contrasenas.cifrar(p.getContrasenaNueva()));
        u.setDebeCambiarContrasena(false);
        usuarios.save(u);
        c.setUsado(true);
        codigos.save(c);
        sesiones.cerrarTodas(u.getId()); // por seguridad, se cierran las sesiones abiertas
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mensaje", "Tu contrasena quedo cambiada. Ya puedes iniciar sesion.");
        return r;
    }

    // =====================================================================
    // Ayudas
    // =====================================================================

    public Rol rol(String id) {
        return roles.findById(id)
                .orElseThrow(() -> new ErrorApp(HttpStatus.SERVICE_UNAVAILABLE, "Falta el rol " + id));
    }

    public OrigenCuenta origen(String id) {
        return origenes.findById(id)
                .orElseThrow(() -> new ErrorApp(HttpStatus.SERVICE_UNAVAILABLE, "Falta el origen " + id));
    }

    private String limpiarCorreo(String valor) {
        String c = valor == null ? "" : valor.trim().toLowerCase();
        if (!CORREO.matcher(c).matches() || c.length() > 120) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Ese correo no parece valido");
        }
        return c;
    }

    /** El telefono se guarda con 10 digitos, sin espacios ni guiones. */
    private String limpiarTelefono(String valor, boolean obligatorio) {
        String t = valor == null ? "" : valor.replaceAll("\\D", "");
        if (t.isEmpty()) {
            if (obligatorio) {
                throw new ErrorApp(HttpStatus.BAD_REQUEST, "Escribe tu telefono a 10 digitos");
            }
            return null;
        }
        if (!SOLO_DIEZ_DIGITOS.matcher(t).matches()) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "El telefono debe tener exactamente 10 digitos");
        }
        return t;
    }

    private void revisarContrasena(String valor) {
        if (valor == null || valor.length() < 8) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "La contrasena debe tener al menos 8 caracteres");
        }
        if (!valor.matches(".*\\d.*") || !valor.matches(".*[A-Za-z].*")) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "La contrasena debe llevar letras y numeros");
        }
    }
}
