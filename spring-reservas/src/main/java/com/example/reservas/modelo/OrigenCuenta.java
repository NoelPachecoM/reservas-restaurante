package com.example.reservas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Catalogo de la base de datos original: tabla origenes_cuenta. */
@Entity
@Table(name = "origenes_cuenta")
@Data
@NoArgsConstructor
public class OrigenCuenta {

    @Id
    @Column(name = "id_origen_cuenta", length = 10)
    private String id;

    @Column(name = "nombre_origen_cuenta", nullable = false, length = 30)
    private String nombre;

    public OrigenCuenta(String id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
