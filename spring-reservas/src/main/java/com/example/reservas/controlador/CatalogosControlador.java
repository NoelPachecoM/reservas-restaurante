package com.example.reservas.controlador;

import com.example.reservas.modelo.FechaBloqueada;
import com.example.reservas.modelo.Platillo;
import com.example.reservas.modelo.Zona;
import com.example.reservas.repositorio.FechaBloqueadaRepositorio;
import com.example.reservas.repositorio.PlatilloRepositorio;
import com.example.reservas.repositorio.ZonaRepositorio;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Los catalogos que el administrador da de alta, baja y cambia. Todos heredan de CrudBase. */
public final class CatalogosControlador {

    private CatalogosControlador() {
    }

    @RestController
    @RequestMapping("/api/admin/catalogos/platillos")
    public static class Platillos extends CrudBase<Platillo> {
        public Platillos(PlatilloRepositorio repo) {
            super(repo);
        }
    }

    @RestController
    @RequestMapping("/api/admin/catalogos/zonas")
    public static class Zonas extends CrudBase<Zona> {
        public Zonas(ZonaRepositorio repo) {
            super(repo);
        }
    }

    @RestController
    @RequestMapping("/api/admin/catalogos/fechas-bloqueadas")
    public static class FechasBloqueadas extends CrudBase<FechaBloqueada> {
        public FechasBloqueadas(FechaBloqueadaRepositorio repo) {
            super(repo);
        }
    }
}
