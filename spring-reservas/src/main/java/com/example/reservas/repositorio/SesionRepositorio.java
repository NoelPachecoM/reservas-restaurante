package com.example.reservas.repositorio;

import com.example.reservas.modelo.Sesion;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SesionRepositorio extends JpaRepository<Sesion, Integer> {

    Optional<Sesion> findByTokenHash(String tokenHash);

    // Borrados de un golpe: dos peticiones pueden limpiar las mismas sesiones a la vez
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from Sesion s where s.usuarioId = :usuarioId")
    int borrarDeUsuario(@Param("usuarioId") Integer usuarioId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from Sesion s where s.expiraEn < :momento")
    int borrarVencidas(@Param("momento") LocalDateTime momento);
}
