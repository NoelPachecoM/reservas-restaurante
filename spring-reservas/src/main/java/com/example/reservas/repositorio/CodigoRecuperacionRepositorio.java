package com.example.reservas.repositorio;

import com.example.reservas.modelo.CodigoRecuperacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CodigoRecuperacionRepositorio extends JpaRepository<CodigoRecuperacion, Integer> {

    Optional<CodigoRecuperacion> findFirstByIdUsuarioAndUsadoFalseOrderByIdDesc(Integer idUsuario);
}
