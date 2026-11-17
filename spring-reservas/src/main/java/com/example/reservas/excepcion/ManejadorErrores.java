package com.example.reservas.excepcion;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Convierte los ErrorApp en respuestas JSON como {"error": "mensaje"}. */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(ErrorApp.class)
    public ResponseEntity<Map<String, String>> manejarArco(ErrorApp e) {
        return ResponseEntity.status(e.getEstado()).body(Map.of("error", e.getMessage()));
    }
}
