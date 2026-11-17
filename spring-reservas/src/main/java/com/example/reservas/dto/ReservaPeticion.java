package com.example.reservas.dto;

import java.util.List;
import lombok.Data;

/** Datos que manda la pagina para crear o cambiar una reserva. */
@Data
public class ReservaPeticion {

    private String fecha;          // AAAA-MM-DD
    private String hora;           // HH:mm
    private Integer personas;
    private Integer idMesa;
    private String ocasion;        // Cumpleanos, aniversario, negocios...
    private String notas;          // alergias, silla para bebe, etc.

    // Solo cuando la reserva la registra el personal por telefono o en la puerta
    private String nombre;
    private String telefono;
    private String correo;

    // Opcional: el cliente puede dejar su pedido desde que reserva
    private List<LineaPeticion> lineas;
}
