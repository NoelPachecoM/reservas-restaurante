package com.example.reservas.modelo;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tabla NUEVA. Cliente que queria reservar y no habia mesa: se le avisa si se libera una. */
@Entity
@Table(name = "lista_espera")
@Data
@NoArgsConstructor
public class ListaEspera implements ConId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_espera")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_persona", nullable = false)
    private Persona persona;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    @Column(nullable = false)
    private Integer personas = 2;

    // PENDIENTE, AVISADO o ATENDIDO
    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();
}
