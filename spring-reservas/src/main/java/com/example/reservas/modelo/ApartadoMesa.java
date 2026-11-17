package com.example.reservas.modelo;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tabla NUEVA. Mesa que alguien esta eligiendo en este momento: queda apartada unos minutos
 * mientras termina de reservar, para que dos personas no se queden con la misma.
 * Los apartados vencidos se borran solos.
 */
@Entity
@Table(name = "apartados_mesa")
@Data
@NoArgsConstructor
public class ApartadoMesa implements ConId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_apartado")
    private Integer id;

    @Column(name = "id_mesa", nullable = false)
    private Integer idMesa;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    // Quien lo esta apartando (su token de sesion, o un identificador del navegador)
    @Column(nullable = false, length = 100)
    private String duenio;

    @Column(nullable = false)
    private LocalDateTime expira;
}
