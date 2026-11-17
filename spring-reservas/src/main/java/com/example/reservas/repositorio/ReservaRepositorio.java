package com.example.reservas.repositorio;

import com.example.reservas.modelo.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReservaRepositorio extends JpaRepository<Reserva, Integer> {

    List<Reserva> findByFecha(LocalDate fecha);

    List<Reserva> findByFechaBetween(LocalDate desde, LocalDate hasta);

    List<Reserva> findByPersona_IdOrderByFechaDescHoraDesc(Integer idPersona);

    List<Reserva> findAllByOrderByFechaDescHoraDesc();

    Optional<Reserva> findByCodigo(String codigo);
}
