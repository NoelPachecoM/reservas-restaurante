package com.example.reservas.repositorio;

import com.example.reservas.modelo.SolicitudCuenta;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SolicitudCuentaRepositorio extends JpaRepository<SolicitudCuenta, Integer> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from SolicitudCuenta s where s.expira < :momento")
    int borrarVencidas(@Param("momento") LocalDateTime momento);

    /** Al empezar un tramite nuevo se tira el anterior del mismo tipo. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from SolicitudCuenta s where s.idUsuario = :idUsuario and s.tipo = :tipo")
    int borrarDeUsuarioYTipo(@Param("idUsuario") Integer idUsuario, @Param("tipo") String tipo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from SolicitudCuenta s where s.idUsuario = :idUsuario")
    int borrarDeUsuario(@Param("idUsuario") Integer idUsuario);
}
