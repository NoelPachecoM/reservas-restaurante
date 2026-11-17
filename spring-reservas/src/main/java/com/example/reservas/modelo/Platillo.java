package com.example.reservas.modelo;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Platillo de la carta. Las etiquetas y los alergenos son columnas NUEVAS. */
@Entity
@Table(name = "platillos")
@Data
@NoArgsConstructor
public class Platillo implements ConId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_platillo")
    private Integer id;

    @Column(name = "nombre_platillo", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion_platillo", length = 255)
    private String descripcion;

    @ManyToOne
    @JoinColumn(name = "id_categoria_platillo", nullable = false)
    private CategoriaPlatillo categoria;

    @Column(name = "precio_platillo", nullable = false)
    private Double precio = 0.0;

    @Column(name = "imagen_platillo", length = 255)
    private String imagen;

    @Column(name = "platillo_activo", nullable = false)
    private Boolean activo = true;

    // Separadas por comas: vegetariano, picante, sin gluten...
    @Column(name = "etiquetas_platillo", length = 200)
    private String etiquetas;

    @Column(name = "alergenos_platillo", length = 200)
    private String alergenos;
}
