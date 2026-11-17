package com.example.reservas.modelo;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Pedido de una reserva. Una reserva puede tener varias rondas de pedidos. */
@Entity
@Table(name = "pedidos")
@Data
@NoArgsConstructor
public class Pedido implements ConId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_reserva", nullable = false)
    private Reserva reserva;

    @ManyToOne
    @JoinColumn(name = "id_estado_pedido", nullable = false)
    private EstadoPedido estado;

    @Column(name = "fecha_registro_pedido", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "id_pedido")
    @OrderBy("id")
    private List<PedidoDetalle> lineas = new ArrayList<>();
}
