package com.example.reservas.excepcion;

import org.springframework.http.HttpStatus;

public class ErrorApp extends RuntimeException {

    private final HttpStatus estado;

    public ErrorApp(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }
}
