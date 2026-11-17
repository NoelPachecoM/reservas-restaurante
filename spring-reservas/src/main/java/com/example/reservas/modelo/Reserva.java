package com.example.reservas.modelo;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reserva de una mesa. Codigo de confirmacion, ocasion, notas y las horas reales de
 * llegada y salida son columnas NUEVAS.
 */
@Entity
@Table(name = "reservas")
@Data
@NoArgsConstructor
public class Reserva implements ConId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reserva")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_persona", nullable = false)
    private Persona persona;

    @ManyToOne
    @JoinColumn(name = "id_mesa", nullable = false)
    private Mesa mesa;

    @Column(name = "fecha_reserva", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_reserva", nullable = false)
    private LocalTime hora;

    @Column(name = "cantidad_personas", nullable = false)
    private Integer personas = 2;

    @ManyToOne
    @JoinColumn(name = "id_estado_reserva", nullable = false)
    private EstadoReserva estado;

    // Empleado que la registro. Vacio si la hizo el propio cliente.
    @ManyToOne
    @JoinColumn(name = "id_usuario_registro")
    private Usuario registradaPor;

    @Column(name = "fecha_registro_reserva", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @Column(name = "codigo_confirmacion", length = 12)
    private String codigo;

    @Column(name = "ocasion_reserva", length = 60)
    private String ocasion;

    @Column(name = "notas_reserva", length = 300)
    private String notas;

    @Column(name = "hora_llegada")
    private LocalDateTime horaLlegada;

    @Column(name = "hora_salida")
    private LocalDateTime horaSalida;
}
