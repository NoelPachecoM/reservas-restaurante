package com.example.reservas.repositorio;

import com.example.reservas.modelo.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MesaRepositorio extends JpaRepository<Mesa, Integer> {

    List<Mesa> findByActivaTrue();
}
