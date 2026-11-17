package com.example.reservas.dto;

import lombok.Data;

/** Lo que manda la pagina en los tramites de datos personales (baja y cambio de correo). */
@Data
public class ArcoPeticion {

    /** Siempre se pide, aunque la sesion este abierta. */
    private String contrasenaActual;

    /** Solo al empezar un cambio de correo. */
    private String correoNuevo;

    /** El tramite que ya se empezo. */
    private Integer solicitudId;

    /** El codigo que llego al correo. */
    private String codigo;
}
