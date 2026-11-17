package com.example.reservas.config;

import com.example.reservas.excepcion.ErrorApp;
import com.example.reservas.modelo.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

/** Ayuda para que los controladores lean el usuario que dejo ProteccionApi. */
public final class SesionActual {

    private SesionActual() {
    }

    public static Usuario usuario(HttpServletRequest request) {
        Object valor = request.getAttribute(ProteccionApi.USUARIO);
        if (valor instanceof Usuario u) return u;
        throw new ErrorApp(HttpStatus.UNAUTHORIZED, "Inicia sesion para continuar");
    }

    /** El token tal como llego en el encabezado Authorization (o null). */
    public static String token(HttpServletRequest request) {
        String encabezado = request.getHeader("Authorization");
        if (encabezado == null || !encabezado.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        return encabezado.substring(7).trim();
    }
}
