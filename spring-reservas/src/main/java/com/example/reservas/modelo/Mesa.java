package com.example.reservas.modelo;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mesa del restaurante. La posicion, la forma y la zona son columnas NUEVAS:
 * son las que permiten dibujar el croquis y que el cliente elija donde sentarse.
 */
@Entity
@Table(name = "mesas")
@Data
@NoArgsConstructor
public class Mesa implements ConId {

    // El numero de mesa lo pone el administrador (no es autonumerico)
    @Id
    @Column(name = "id_mesa")
    private Integer id;

    @Column(name = "capacidad_mesa", nullable = false)
    private Integer capacidad = 4;

    @Column(name = "mesa_activa", nullable = false)
    private Boolean activa = true;

    @ManyToOne
    @JoinColumn(name = "id_zona")
    private Zona zona;

    // Centro de la mesa en el plano, en metros
    @Column(name = "posicion_x")
    private Double x = 1.0;

    @Column(name = "posicion_y")
    private Double y = 1.0;

    // REDONDA o CUADRADA
    @Column(name = "forma_mesa", length = 20)
    private String forma = "REDONDA";

    // Etiquetas separadas por comas para filtrar: ventana, accesible, tranquila...
    @Column(name = "etiquetas_mesa", length = 200)
    private String etiquetas;
}
