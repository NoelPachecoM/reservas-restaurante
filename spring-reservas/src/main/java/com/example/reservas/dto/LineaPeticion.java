package com.example.reservas.dto;

import lombok.Data;

/** Un platillo dentro de un pedido. */
@Data
public class LineaPeticion {

    private Integer idPlatillo;
    private Integer cantidad;
    private String notas;   // "sin cebolla", "termino medio"
}
