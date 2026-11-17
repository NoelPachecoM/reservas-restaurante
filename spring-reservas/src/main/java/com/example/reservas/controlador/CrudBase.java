package com.example.reservas.controlador;

import com.example.reservas.excepcion.ErrorApp;
import com.example.reservas.modelo.ConId;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * CRUD comun de los catalogos del administrador (platillos, zonas, fechas bloqueadas).
 * Cada catalogo solo declara su ruta y su repositorio (ver CatalogosControlador).
 */
public abstract class CrudBase<T extends ConId> {

    protected final JpaRepository<T, Integer> repo;

    protected CrudBase(JpaRepository<T, Integer> repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<T> listar() {
        return repo.findAll();
    }

    @PostMapping
    public T crear(@RequestBody T nuevo) {
        nuevo.setId(null);   // el id lo pone la base de datos
        return repo.save(nuevo);
    }

    @PutMapping("/{id}")
    public T actualizar(@PathVariable Integer id, @RequestBody T datos) {
        if (!repo.existsById(id)) throw new ErrorApp(HttpStatus.NOT_FOUND, "No existe ese registro");
        datos.setId(id);
        return repo.save(datos);
    }

    @DeleteMapping("/{id}")
    public void borrar(@PathVariable Integer id) {
        try {
            repo.deleteById(id);
            repo.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ErrorApp(HttpStatus.CONFLICT,
                    "No se puede borrar porque ya se usa en alguna reserva o pedido. Mejor desactivalo.");
        }
    }
}
