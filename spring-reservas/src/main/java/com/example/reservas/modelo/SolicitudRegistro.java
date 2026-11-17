package com.example.reservas.modelo;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tabla NUEVA. Registro a medias: alguien lleno el formulario y se le mando un codigo
 * de 6 digitos al correo, pero la cuenta TODAVIA NO EXISTE. Se crea hasta que escribe
 * el codigo; asi se comprueba que el correo es de verdad suyo.
 *
 * Los datos se guardan aqui mientras tanto, con la contrasena ya cifrada.
 */
@Entity
@Table(name = "solicitudes_registro")
@Data
@NoArgsConstructor
public class SolicitudRegistro implements ConId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Integer id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 20)
    private String telefono;

    @Column(nullable = false, length = 120)
    private String correo;

    /** Ya cifrada: aqui nunca se guarda la contrasena tal cual. */
    @Column(name = "contrasena_hash", nullable = false)
    private String contrasenaHash;

    /** CLIENTE o EMPLEADO: el rol que va a tener la cuenta cuando se cree. */
    @Column(name = "id_rol", nullable = false, length = 30)
    private String rol = "CLIENTE";

    @Column(nullable = false, length = 10)
    private String codigo;

    @Column(nullable = false)
    private LocalDateTime expira;

    @Column(name = "enviado_en", nullable = false)
    private LocalDateTime enviadoEn = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean usado = false;

    private Integer intentos = 0;
}
