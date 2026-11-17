package com.example.reservas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Catalogo de la base de datos original: tabla roles. */
@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
public class Rol {

    @Id
    @Column(name = "id_rol", length = 20)
    private String id;

    @Column(name = "nombre_rol", nullable = false, length = 30)
    private String nombre;

    public Rol(String id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
