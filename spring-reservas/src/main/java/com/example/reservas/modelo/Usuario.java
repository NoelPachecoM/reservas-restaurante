package com.example.reservas.modelo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Cuenta de acceso. Una persona tiene como maximo una cuenta. */
@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
public class Usuario implements ConId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer id;

    @OneToOne
    @JoinColumn(name = "id_persona", nullable = false, unique = true)
    private Persona persona;

    @Column(name = "nombre_usuario", nullable = false, unique = true, length = 120)
    private String usuario;

    // WRITE_ONLY: se acepta al crear, pero nunca aparece en las respuestas JSON
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "contrasena_hash", nullable = false)
    private String contrasenaHash;

    @ManyToOne
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    @ManyToOne
    @JoinColumn(name = "id_origen_cuenta", nullable = false)
    private OrigenCuenta origen;

    @Column(name = "usuario_activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "debe_cambiar_contrasena", nullable = false)
    private Boolean debeCambiarContrasena = false;

    @Column(name = "fecha_registro_usuario", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();
}
