package com.example.reservas.modelo;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tabla NUEVA. Codigo que se manda al correo para restablecer la contrasena. */
@Entity
@Table(name = "codigos_recuperacion")
@Data
@NoArgsConstructor
public class CodigoRecuperacion implements ConId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_codigo")
    private Integer id;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(nullable = false, length = 10)
    private String codigo;

    @Column(nullable = false)
    private LocalDateTime expira;

    @Column(nullable = false)
    private Boolean usado = false;

    private Integer intentos = 0;
}
