package com.example.reservas.dto;

import lombok.Data;

/** Cambios que una persona hace en su propia cuenta. Siempre pide la contrasena actual. */
@Data
public class CuentaPeticion {

    private String contrasenaActual;
    private String nombre;
    private String telefono;
    private String correo;
    private String contrasenaNueva;
}
