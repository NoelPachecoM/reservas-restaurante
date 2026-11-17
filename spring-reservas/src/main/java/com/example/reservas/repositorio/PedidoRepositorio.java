package com.example.reservas.repositorio;

import com.example.reservas.modelo.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PedidoRepositorio extends JpaRepository<Pedido, Integer> {

    List<Pedido> findByReserva_IdOrderByIdAsc(Integer idReserva);

    List<Pedido> findByEstado_IdInOrderByIdAsc(List<String> estados);
}
