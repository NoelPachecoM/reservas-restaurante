package com.example.reservas.repositorio;

import com.example.reservas.modelo.Platillo;
import org.springframework.data.jpa.repository.JpaRepository;


public interface PlatilloRepositorio extends JpaRepository<Platillo, Integer> {

}
