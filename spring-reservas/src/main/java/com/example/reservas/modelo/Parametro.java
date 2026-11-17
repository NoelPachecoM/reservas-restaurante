package com.example.reservas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuracion del restaurante: hora de apertura y cierre, duracion de la reserva,
 * codigo de empleado, aforo, tolerancia y los datos que se muestran en la pagina.
 * Se guarda como texto, igual que en la base de datos original.
 */
@Entity
@Table(name = "parametros")
@Data
@NoArgsConstructor
public class Parametro {

    @Id
    @Column(name = "id_parametro", length = 40)
    private String id;

    @Column(name = "valor_parametro", nullable = false, length = 300)
    private String valor;

    @Column(name = "descripcion_parametro", length = 255)
    private String descripcion;

    public Parametro(String id, String valor, String descripcion) {
        this.id = id;
        this.valor = valor;
        this.descripcion = descripcion;
    }
}
