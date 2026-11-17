package com.example.reservas.repositorio;

import com.example.reservas.modelo.ListaEspera;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ListaEsperaRepositorio extends JpaRepository<ListaEspera, Integer> {

    List<ListaEspera> findByOrderByFechaAscHoraAsc();
}
