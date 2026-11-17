package com.example.reservas.dto;

import java.util.List;
import lombok.Data;

/** Pedido completo que se manda a la cocina. */
@Data
public class PedidoPeticion {

    private Integer idReserva;
    private List<LineaPeticion> lineas;
}
