package com.example.reservas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Catalogo de la base de datos original: tabla estados_pedido. */
@Entity
@Table(name = "estados_pedido")
@Data
@NoArgsConstructor
public class EstadoPedido {

    @Id
    @Column(name = "id_estado_pedido", length = 20)
    private String id;

    @Column(name = "nombre_estado_pedido", nullable = false, length = 30)
    private String nombre;

    public EstadoPedido(String id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
