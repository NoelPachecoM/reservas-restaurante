package com.example.reservas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Catalogo de la base de datos original: tabla categorias_platillo. */
@Entity
@Table(name = "categorias_platillo")
@Data
@NoArgsConstructor
public class CategoriaPlatillo {

    @Id
    @Column(name = "id_categoria_platillo", length = 20)
    private String id;

    @Column(name = "nombre_categoria_platillo", nullable = false, length = 30)
    private String nombre;

    public CategoriaPlatillo(String id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
