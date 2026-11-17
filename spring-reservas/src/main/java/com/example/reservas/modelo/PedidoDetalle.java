package com.example.reservas.modelo;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una linea del pedido. CAMBIO respecto a la base de datos original: aqui la linea tiene su
 * propio id en lugar de usar la pareja pedido + platillo como llave. Asi se puede pedir dos
 * veces el mismo platillo con notas distintas (uno termino medio y otro bien cocido).
 */
@Entity
@Table(name = "pedido_detalle")
@Data
@NoArgsConstructor
public class PedidoDetalle implements ConId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_platillo", nullable = false)
    private Platillo platillo;

    @Column(name = "cantidad_platillo", nullable = false)
    private Integer cantidad = 1;

    // Precio de una unidad al momento de pedir: se guarda para que el historial no cambie
    @Column(name = "precio_unitario_platillo", nullable = false)
    private Double precioUnitario = 0.0;

    @Column(name = "notas_detalle", length = 200)
    private String notas;
}
