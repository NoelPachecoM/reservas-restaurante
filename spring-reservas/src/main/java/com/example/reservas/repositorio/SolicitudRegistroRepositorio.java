package com.example.reservas.repositorio;

import com.example.reservas.modelo.SolicitudRegistro;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SolicitudRegistroRepositorio extends JpaRepository<SolicitudRegistro, Integer> {

    /** Limpieza: las solicitudes que ya vencieron no sirven de nada. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from SolicitudRegistro s where s.expira < :momento")
    int borrarVencidas(@Param("momento") LocalDateTime momento);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from SolicitudRegistro s where lower(s.correo) = lower(:correo)")
    int borrarDeCorreo(@Param("correo") String correo);
}
