package com.example.reservas.repositorio;

import com.example.reservas.modelo.FechaBloqueada;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface FechaBloqueadaRepositorio extends JpaRepository<FechaBloqueada, Integer> {

    boolean existsByFecha(LocalDate fecha);

    List<FechaBloqueada> findByFechaBetween(LocalDate desde, LocalDate hasta);
}
