package com.example.reservas.repositorio;

import com.example.reservas.modelo.ApartadoMesa;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ApartadoMesaRepositorio extends JpaRepository<ApartadoMesa, Integer> {

    List<ApartadoMesa> findByFechaAndHoraAndExpiraAfter(LocalDate fecha, LocalTime hora, LocalDateTime ahora);

    Optional<ApartadoMesa> findByIdMesaAndFechaAndHora(Integer idMesa, LocalDate fecha, LocalTime hora);

    /**
     * Los borrados van como consulta directa (y no con un deleteBy... normal) a proposito.
     *
     * Un deleteBy... normal primero LEE las filas y luego borra una por una: si entre la
     * lectura y el borrado otra peticion ya borro esa misma fila (cosa muy normal aqui,
     * porque varias pantallas limpian los apartados vencidos al mismo tiempo), Hibernate
     * truena con "Unexpected row count". Asi se borra de un golpe y no pasa nada si ya
     * no estaba.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from ApartadoMesa a where a.expira < :momento")
    int borrarVencidos(@Param("momento") LocalDateTime momento);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from ApartadoMesa a where a.duenio = :duenio")
    int borrarDe(@Param("duenio") String duenio);
}
