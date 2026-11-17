package com.example.reservas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Catalogo de la base de datos original: tabla estados_reserva. */
@Entity
@Table(name = "estados_reserva")
@Data
@NoArgsConstructor
public class EstadoReserva {

    @Id
    @Column(name = "id_estado_reserva", length = 20)
    private String id;

    @Column(name = "nombre_estado_reserva", nullable = false, length = 30)
    private String nombre;

    public EstadoReserva(String id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
