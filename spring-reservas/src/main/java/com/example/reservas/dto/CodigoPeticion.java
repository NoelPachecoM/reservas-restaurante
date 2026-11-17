package com.example.reservas.dto;

import lombok.Data;

/** Recuperar la contrasena: primero se pide el codigo, luego se manda con la nueva. */
@Data
public class CodigoPeticion {

    private String correo;
    private String codigo;
    private String contrasenaNueva;
}
