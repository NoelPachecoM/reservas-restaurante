package com.example.reservas.modelo;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tabla NUEVA. Dia en el que el restaurante no recibe reservas (descanso, festivo, evento privado). */
@Entity
@Table(name = "fechas_bloqueadas")
@Data
@NoArgsConstructor
public class FechaBloqueada implements ConId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_fecha_bloqueada")
    private Integer id;

    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    @Column(length = 200)
    private String motivo;
}
