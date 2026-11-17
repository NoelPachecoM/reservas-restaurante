package com.example.reservas.repositorio;

import com.example.reservas.modelo.Persona;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PersonaRepositorio extends JpaRepository<Persona, Integer> {

    Optional<Persona> findFirstByCorreoIgnoreCase(String correo);
}
