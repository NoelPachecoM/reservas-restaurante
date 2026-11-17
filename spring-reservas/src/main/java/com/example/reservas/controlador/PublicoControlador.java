package com.example.reservas.controlador;

import com.example.reservas.modelo.Platillo;
import com.example.reservas.modelo.Zona;
import com.example.reservas.repositorio.CategoriaPlatilloRepositorio;
import com.example.reservas.repositorio.PlatilloRepositorio;
import com.example.reservas.servicio.Config;
import com.example.reservas.servicio.ReservaServicio;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/**
 * Lo que se puede ver sin iniciar sesion: los datos del restaurante, la carta, el calendario,
 * los horarios, el croquis de las mesas y la consulta de una reserva con su codigo.
 *
 * Aqui nunca se devuelve el nombre ni el telefono de quien ocupa una mesa: el visitante
 * solo necesita saber si la mesa esta libre u ocupada.
 */
@RestController
@RequestMapping("/api/publico")
public class PublicoControlador {

    private final Config config;
    private final ReservaServicio servicio;
    private final PlatilloRepositorio platillos;
    private final CategoriaPlatilloRepositorio categorias;
    /** true cuando el servidor cargo las cuentas y reservas de ejemplo. */
    private final boolean demo;

    public PublicoControlador(Config config, ReservaServicio servicio,
                              PlatilloRepositorio platillos, CategoriaPlatilloRepositorio categorias,
                              @org.springframework.beans.factory.annotation.Value(
                                      "${datos.ejemplos:true}") boolean demo) {
        this.demo = demo;
        this.config = config;
        this.servicio = servicio;
        this.platillos = platillos;
        this.categorias = categorias;
    }

    /** Nombre, frase, direccion, telefono, horario y fotos del restaurante. */
    @GetMapping("/restaurante")
    public Map<String, Object> restaurante() {
        Map<String, Object> r = new LinkedHashMap<>(config.restaurante());
        r.put("duracionReserva", config.duracion());
        r.put("minutosApartado", config.numero(Config.APARTADO, 10));
        r.put("diasParaReservar", config.numero(Config.DIAS_VISTA, 60));
        r.put("horasParaCambiar", config.numero(Config.HORAS_CAMBIO, 4));
        r.put("franjas", servicio.franjas().stream().map(Objects::toString).toList());
        // La pagina de entrar solo enseña las cuentas de prueba si esto es una demostracion
        r.put("demo", demo);
        return r;
    }

    /** La carta, agrupada por categoria y en el orden en que se come. */
    @GetMapping("/carta")
    public List<Map<String, Object>> carta() {
        List<Platillo> activos = platillos.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .toList();
        List<Map<String, Object>> salida = new ArrayList<>();
        for (var c : categorias.findAll()) {
            List<Platillo> suyos = activos.stream()
                    .filter(p -> p.getCategoria().getId().equals(c.getId()))
                    .sorted(Comparator.comparing(Platillo::getNombre))
                    .toList();
            if (suyos.isEmpty()) continue;
            Map<String, Object> grupo = new LinkedHashMap<>();
            grupo.put("id", c.getId());
            grupo.put("nombre", c.getNombre());
            grupo.put("platillos", suyos);
            salida.add(grupo);
        }
        return salida;
    }

    /** Las areas del restaurante, para dibujar el fondo del croquis. */
    @GetMapping("/zonas")
    public List<Zona> zonas() {
        return servicio.zonas();
    }

    /** Estado de cada dia del mes: libre, parcial, ocupado, bloqueado o pasado. */
    @GetMapping("/dias")
    public List<Map<String, Object>> dias(@RequestParam int anio, @RequestParam int mes,
                                          @RequestParam(defaultValue = "2") int personas) {
        return servicio.dias(anio, mes, Math.max(1, personas));
    }

    /** Horarios de un dia y cuantas mesas quedan en cada uno. */
    @GetMapping("/horarios")
    public List<Map<String, Object>> horarios(@RequestParam String fecha,
                                              @RequestParam(defaultValue = "2") int personas,
                                              @RequestParam(required = false) String clave) {
        return servicio.horariosDe(servicio.fecha(fecha), Math.max(1, personas), clave(clave), null);
    }

    /** Croquis de las mesas a una fecha y hora, sin datos de los demas clientes. */
    @GetMapping("/croquis")
    public List<Map<String, Object>> croquis(@RequestParam String fecha, @RequestParam String hora,
                                             @RequestParam(required = false) String clave) {
        List<Map<String, Object>> mesas = servicio.croquis(
                servicio.fecha(fecha), servicio.hora(hora), clave(clave), null);
        mesas.forEach(m -> {
            m.remove("ocupadaPor");
            m.remove("idReserva");
        });
        return mesas;
    }

    /** Aparta la mesa unos minutos mientras la persona termina de reservar. */
    @PostMapping("/apartar")
    public Map<String, Object> apartar(@RequestParam Integer idMesa, @RequestParam String fecha,
                                       @RequestParam String hora, @RequestParam(required = false) String clave) {
        return servicio.apartar(idMesa, servicio.fecha(fecha), servicio.hora(hora), clave(clave));
    }

    /** Suelta la mesa apartada (si la persona se arrepiente o cambia de hora). */
    @PostMapping("/soltar")
    public Map<String, Object> soltar(@RequestParam(required = false) String clave) {
        servicio.soltar(clave(clave));
        return Map.of("mensaje", "Mesa liberada");
    }

    /** Consultar una reserva con el codigo de confirmacion, sin iniciar sesion. */
    @GetMapping("/reserva/{codigo}")
    public Map<String, Object> porCodigo(@PathVariable String codigo) {
        Map<String, Object> r = new LinkedHashMap<>(servicio.porCodigo(codigo));
        r.remove("telefono");
        r.remove("correo");
        return r;
    }

    /**
     * Identifica a quien esta apartando una mesa sin tener cuenta. La pagina genera una
     * clave al azar y la guarda en el navegador; sirve para saber que la mesa apartada es suya.
     */
    private String clave(String valor) {
        if (valor == null || valor.isBlank()) return "visita";
        return valor.length() > 60 ? valor.substring(0, 60) : valor.trim();
    }

    /** Fecha de hoy segun el servidor, para que la pagina no dependa del reloj del celular. */
    @GetMapping("/hoy")
    public Map<String, Object> hoy() {
        return Map.of("fecha", LocalDate.now().toString());
    }
}
