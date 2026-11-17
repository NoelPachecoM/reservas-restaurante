package com.example.reservas.repositorio;

import com.example.reservas.modelo.EstadoReserva;
import org.springframework.data.jpa.repository.JpaRepository;


public interface EstadoReservaRepositorio extends JpaRepository<EstadoReserva, String> {

}
