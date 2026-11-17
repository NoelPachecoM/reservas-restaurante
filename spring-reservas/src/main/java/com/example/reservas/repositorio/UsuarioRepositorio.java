package com.example.reservas.repositorio;

import com.example.reservas.modelo.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByUsuarioIgnoreCase(String usuario);

    boolean existsByUsuarioIgnoreCase(String usuario);

    boolean existsByRol_Id(String idRol);

    Optional<Usuario> findByPersona_Id(Integer idPersona);
}
