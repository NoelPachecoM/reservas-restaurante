package com.example.reservas.modelo;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tabla NUEVA. Area del restaurante (terraza, salon, barra, privado).
 * Sus medidas sirven para dibujar el fondo del croquis; estan en metros.
 */
@Entity
@Table(name = "zonas")
@Data
@NoArgsConstructor
public class Zona implements ConId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_zona")
    private Integer id;

    @Column(name = "nombre_zona", nullable = false, length = 60)
    private String nombre;

    // Esquina inferior izquierda y tamano del rectangulo, en metros
    private Double x = 0.0;
    private Double y = 0.0;
    private Double ancho = 6.0;
    private Double alto = 6.0;

    // Color de fondo en el croquis (#RRGGBB)
    @Column(length = 9)
    private String color = "#F3EDE0";

    // SALA = se pueden poner mesas; SERVICIO = cocina, banos, entrada (no se reservan)
    @Column(length = 20)
    private String tipo = "SALA";

    @Column(length = 200)
    private String descripcion;

    private Boolean reservable = true;
}
