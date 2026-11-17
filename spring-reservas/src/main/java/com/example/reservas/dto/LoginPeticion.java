package com.example.reservas.dto;

import lombok.Data;

@Data
public class LoginPeticion {

    private String usuario;      // el correo con el que se registro
    private String contrasena;
}
