package com.example.reservas.dto;

import lombok.Data;

/** Registro de un cliente o de un empleado (el empleado necesita el codigo). */
@Data
public class RegistroPeticion {

    private String nombre;
    private String telefono;
    private String correo;
    private String contrasena;
    private String codigoEmpleado;   // solo para registrarse como empleado
}
