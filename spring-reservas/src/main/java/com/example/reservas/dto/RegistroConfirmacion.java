package com.example.reservas.dto;

import lombok.Data;

/** Segundo paso del registro: el codigo que llego al correo. */
@Data
public class RegistroConfirmacion {

    private Integer solicitudId;
    private String codigo;
}
