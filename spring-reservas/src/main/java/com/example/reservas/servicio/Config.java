package com.example.reservas.servicio;

import com.example.reservas.modelo.Parametro;
import com.example.reservas.repositorio.ParametroRepositorio;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Lee la configuracion del restaurante de la tabla parametros, para que nada de esto
 * quede escrito en el codigo. Si un parametro no existe, se usa el valor de respaldo.
 */
@Service
public class Config {

    public static final String APERTURA = "hora_apertura";
    public static final String CIERRE = "hora_cierre";
    public static final String DURACION = "duracion_reserva_min";
    public static final String CODIGO_EMPLEADO = "codigo_empleado";
    public static final String PASO = "minutos_entre_horarios";
    public static final String AFORO = "aforo_por_franja";
    public static final String TOLERANCIA = "tolerancia_min";
    public static final String APARTADO = "minutos_apartado";
    public static final String DIAS_VISTA = "dias_para_reservar";
    public static final String HORAS_CAMBIO = "horas_para_cambiar";
    public static final String NOMBRE = "nombre_restaurante";
    public static final String FRASE = "frase_restaurante";
    public static final String DESCRIPCION = "descripcion_restaurante";
    public static final String DIRECCION = "direccion_restaurante";
    public static final String TELEFONO = "telefono_restaurante";
    public static final String FOTOS = "fotos_restaurante";

    private final ParametroRepositorio repo;

    public Config(ParametroRepositorio repo) {
        this.repo = repo;
    }

    public String texto(String id, String respaldo) {
        return repo.findById(id).map(Parametro::getValor).filter(v -> !v.isBlank()).orElse(respaldo);
    }

    public int numero(String id, int respaldo) {
        try {
            return Integer.parseInt(texto(id, String.valueOf(respaldo)).trim());
        } catch (NumberFormatException e) {
            return respaldo;
        }
    }

    public LocalTime hora(String id, String respaldo) {
        try {
            return LocalTime.parse(texto(id, respaldo).trim());
        } catch (RuntimeException e) {
            return LocalTime.parse(respaldo);
        }
    }

    public LocalTime apertura() {
        return hora(APERTURA, "12:00");
    }

    public LocalTime cierre() {
        return hora(CIERRE, "22:00");
    }

    /** Minutos que una reserva ocupa la mesa. */
    public int duracion() {
        return Math.max(30, numero(DURACION, 120));
    }

    /** Cada cuantos minutos empieza una franja (12:00, 12:30, 13:00...). */
    public int paso() {
        return Math.max(15, numero(PASO, 30));
    }

    /** Lo que ve la pagina publica del restaurante. */
    public Map<String, Object> restaurante() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("nombre", texto(NOMBRE, "Restaurante Demo"));
        r.put("frase", texto(FRASE, "Cocina mexicana contemporanea"));
        r.put("descripcion", texto(DESCRIPCION, ""));
        r.put("direccion", texto(DIRECCION, ""));
        r.put("telefono", texto(TELEFONO, ""));
        r.put("fotos", texto(FOTOS, ""));
        r.put("horaApertura", apertura().toString());
        r.put("horaCierre", cierre().toString());
        r.put("duracionMin", duracion());
        r.put("pasoMin", paso());
        r.put("aforo", numero(AFORO, 120));
        r.put("toleranciaMin", numero(TOLERANCIA, 20));
        r.put("minutosApartado", numero(APARTADO, 10));
        r.put("diasParaReservar", numero(DIAS_VISTA, 60));
        r.put("horasParaCambiar", numero(HORAS_CAMBIO, 4));
        return r;
    }

    public void guardar(String id, String valor) {
        Parametro p = repo.findById(id).orElseGet(() -> new Parametro(id, "", null));
        p.setValor(valor == null ? "" : valor);
        repo.save(p);
    }
}
