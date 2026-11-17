package com.example.reservas.config;

import com.example.reservas.modelo.Usuario;
import com.example.reservas.servicio.SesionServicio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Revisa el token de sesion ANTES de llegar a cualquier controlador de /api.
 *
 *   /api/publico/**  -> sin token (inicio, carta, calendario, horarios, croquis, consulta por codigo)
 *   login y registro -> sin token
 *   /api/piso/**     -> empleado o administrador (vista de piso y cocina)
 *   /api/admin/**    -> solo administrador
 *   todo lo demas    -> cualquier cuenta con sesion abierta
 */
@Component
public class ProteccionApi implements HandlerInterceptor {

    /** Nombre del atributo donde queda el usuario de la sesion para los controladores. */
    public static final String USUARIO = "usuarioSesion";

    public static final String ADMIN = "ADMINISTRADOR";
    public static final String EMPLEADO = "EMPLEADO";

    private final SesionServicio sesiones;

    public ProteccionApi(SesionServicio sesiones) {
        this.sesiones = sesiones;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true; // preflight de CORS

        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        if (!ruta.startsWith("/api/")) return true;

        Usuario usuario = sesiones.usuarioDe(SesionActual.token(request));
        if (usuario != null) request.setAttribute(USUARIO, usuario);

        // ---- Publicas ----
        if (ruta.startsWith("/api/publico/")
                || ruta.equals("/api/auth/login")
                || ruta.startsWith("/api/auth/registro")
                || ruta.startsWith("/api/auth/olvide")) {
            return true;
        }

        if (usuario == null) return negar(response, 401, "Inicia sesion para continuar");
        if (Boolean.FALSE.equals(usuario.getActivo())) return negar(response, 403, "Esta cuenta esta desactivada");

        if (ruta.startsWith("/api/admin/")) {
            return esAdmin(usuario) || negar(response, 403, "Esta seccion es solo para el administrador");
        }
        if (ruta.startsWith("/api/piso/")) {
            return esPersonal(usuario) || negar(response, 403, "Esta seccion es solo para el personal");
        }
        return true;
    }

    public static boolean esAdmin(Usuario usuario) {
        return usuario != null && usuario.getRol() != null && ADMIN.equals(usuario.getRol().getId());
    }

    /** Empleado o administrador: los dos pueden trabajar el piso. */
    public static boolean esPersonal(Usuario usuario) {
        if (usuario == null || usuario.getRol() == null) return false;
        String rol = usuario.getRol().getId();
        return ADMIN.equals(rol) || EMPLEADO.equals(rol);
    }

    private boolean negar(HttpServletResponse response, int estado, String mensaje) throws IOException {
        response.setStatus(estado);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + mensaje + "\"}");
        return false;
    }
}
