package com.example.reservas.modelo;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tabla NUEVA. Tramite delicado sobre los datos personales de una cuenta, a medio hacer.
 *
 * Sirve para los dos tramites que no se pueden deshacer:
 *
 *   BAJA           - borrar la cuenta. Se manda UN codigo al correo de la cuenta.
 *   CAMBIO_CORREO  - cambiar el correo. Se manda un codigo al correo ACTUAL (para saber
 *                    que de verdad lo pidio el dueno) y, cuando ese se confirma, OTRO
 *                    codigo al correo NUEVO (para saber que ese correo existe y es suyo).
 *
 * Mientras el tramite no se confirme completo, en la cuenta no cambia nada.
 */
@Entity
@Table(name = "solicitudes_cuenta")
@Data
@NoArgsConstructor
public class SolicitudCuenta implements ConId {

    public static final String BAJA = "BAJA";
    public static final String CAMBIO_CORREO = "CAMBIO_CORREO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud_cuenta")
    private Integer id;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    /** BAJA o CAMBIO_CORREO. */
    @Column(nullable = false, length = 20)
    private String tipo;

    /** Solo en CAMBIO_CORREO: a donde se quiere mover la cuenta. */
    @Column(name = "correo_nuevo", length = 120)
    private String correoNuevo;

    /** El que se manda al correo que la cuenta tiene hoy. */
    @Column(name = "codigo_actual", nullable = false, length = 10)
    private String codigoActual;

    /** Solo en CAMBIO_CORREO: el que se manda al correo nuevo, ya que paso el primero. */
    @Column(name = "codigo_nuevo", length = 10)
    private String codigoNuevo;

    /** true cuando ya se escribio bien el codigo del correo actual. */
    @Column(name = "actual_confirmado", nullable = false)
    private Boolean actualConfirmado = false;

    @Column(nullable = false)
    private LocalDateTime expira;

    @Column(name = "enviado_en", nullable = false)
    private LocalDateTime enviadoEn = LocalDateTime.now();

    @Column(nullable = false)
    private Boolean usado = false;

    private Integer intentos = 0;
}
