package com.example.reservas.modelo;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Quien reserva, tenga o no cuenta en el sistema.
 * Telefono y correo son columnas NUEVAS: sin ellas no hay como confirmar ni recordar una reserva.
 */
@Entity
@Table(name = "personas")
@Data
@NoArgsConstructor
public class Persona implements ConId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_persona")
    private Integer id;

    @Column(name = "nombre_persona", nullable = false, length = 100)
    private String nombre;

    @Column(name = "telefono_persona", length = 20)
    private String telefono;

    @Column(name = "correo_persona", length = 120)
    private String correo;
}
