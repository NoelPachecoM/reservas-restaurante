package com.example.reservas.servicio;

import com.example.reservas.dto.ReservaPeticion;
import com.example.reservas.excepcion.ErrorApp;
import com.example.reservas.modelo.*;
import com.example.reservas.repositorio.*;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de las reservas: horarios disponibles, estado de cada mesa en el croquis,
 * apartado temporal mientras el cliente elige, y el ciclo de vida de la reserva
 * (confirmada, en mesa, finalizada, cancelada, no se presento).
 */
@Service
public class ReservaServicio {

    public static final String CONFIRMADA = "CONFIRMADA";
    public static final String EN_MESA = "EN_MESA";
    public static final String FINALIZADA = "FINALIZADA";
    public static final String CANCELADA = "CANCELADA";
    public static final String NO_SE_PRESENTO = "NO_SE_PRESENTO";
    /** Estados que ocupan la mesa. Solo estos cuentan para los empalmes. */
    private static final List<String> ACTIVOS = List.of(CONFIRMADA, EN_MESA);

    private final ReservaRepositorio reservas;
    private final MesaRepositorio mesas;
    private final PersonaRepositorio personas;
    private final EstadoReservaRepositorio estados;
    private final FechaBloqueadaRepositorio bloqueos;
    private final ApartadoMesaRepositorio apartados;
    private final ListaEsperaRepositorio espera;
    private final ZonaRepositorio zonas;
    private final Config config;
    private final ServicioCorreo correo;
    private final SecureRandom aleatorio = new SecureRandom();

    public ReservaServicio(ReservaRepositorio reservas, MesaRepositorio mesas, PersonaRepositorio personas,
                           EstadoReservaRepositorio estados, FechaBloqueadaRepositorio bloqueos,
                           ApartadoMesaRepositorio apartados, ListaEsperaRepositorio espera,
                           ZonaRepositorio zonas, Config config, ServicioCorreo correo) {
        this.reservas = reservas;
        this.mesas = mesas;
        this.personas = personas;
        this.estados = estados;
        this.bloqueos = bloqueos;
        this.apartados = apartados;
        this.espera = espera;
        this.zonas = zonas;
        this.config = config;
        this.correo = correo;
    }

    // =====================================================================
    // Horarios
    // =====================================================================

    /** Horas a las que puede empezar una reserva. La ultima debe alcanzar a terminar antes del cierre. */
    public List<LocalTime> franjas() {
        List<LocalTime> lista = new ArrayList<>();
        LocalTime tope = config.cierre().minusMinutes(config.duracion());
        for (LocalTime t = config.apertura(); !t.isAfter(tope); t = t.plusMinutes(config.paso())) {
            lista.add(t);
            if (lista.size() > 96) break; // candado por si la configuracion quedara mal
        }
        return lista;
    }

    private boolean seEmpalman(LocalTime a, LocalTime b) {
        int dur = config.duracion();
        LocalTime finA = a.plusMinutes(dur);
        LocalTime finB = b.plusMinutes(dur);
        return a.isBefore(finB) && b.isBefore(finA);
    }

    /** Reservas que ocupan mesa ese dia. */
    private List<Reserva> activasDe(LocalDate fecha) {
        return reservas.findByFecha(fecha).stream()
                .filter(r -> ACTIVOS.contains(r.getEstado().getId()))
                .toList();
    }

    // =====================================================================
    // Croquis
    // =====================================================================

    /**
     * Estado de cada mesa para una fecha y hora: LIBRE, OCUPADA (ya reservada),
     * APARTADA (alguien mas la esta eligiendo) o PROPIA (la reserva que se esta editando).
     */
    @Transactional
    public List<Map<String, Object>> croquis(LocalDate fecha, LocalTime hora, String duenio, Integer idReservaPropia) {
        limpiarApartados();
        List<Reserva> delDia = activasDe(fecha);
        List<ApartadoMesa> vigentes = apartados.findByFechaAndHoraAndExpiraAfter(fecha, hora, LocalDateTime.now());
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Mesa m : mesas.findAll().stream().sorted(Comparator.comparing(Mesa::getId)).toList()) {
            Reserva choca = delDia.stream()
                    .filter(r -> r.getMesa().getId().equals(m.getId()) && seEmpalman(hora, r.getHora()))
                    .findFirst().orElse(null);
            boolean apartada = vigentes.stream()
                    .anyMatch(a -> a.getIdMesa().equals(m.getId()) && !a.getDuenio().equals(duenio));
            String estado;
            if (!Boolean.TRUE.equals(m.getActiva())) {
                estado = "FUERA_DE_SERVICIO";
            } else if (choca != null) {
                estado = choca.getId().equals(idReservaPropia) ? "PROPIA" : "OCUPADA";
            } else if (apartada) {
                estado = "APARTADA";
            } else {
                estado = "LIBRE";
            }
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", m.getId());
            v.put("capacidad", m.getCapacidad());
            v.put("x", m.getX());
            v.put("y", m.getY());
            v.put("forma", m.getForma());
            v.put("etiquetas", m.getEtiquetas());
            v.put("zona", m.getZona() == null ? null : m.getZona().getNombre());
            v.put("idZona", m.getZona() == null ? null : m.getZona().getId());
            v.put("estado", estado);
            // Solo para el personal: quien ocupa la mesa
            v.put("ocupadaPor", choca == null ? null : choca.getPersona().getNombre());
            v.put("idReserva", choca == null ? null : choca.getId());
            salida.add(v);
        }
        return salida;
    }

    /** Mesas libres a esa hora que alcanzan para el grupo, de la mas chica a la mas grande. */
    public List<Integer> mesasQueAlcanzan(LocalDate fecha, LocalTime hora, int personas, String duenio, Integer propia) {
        return croquis(fecha, hora, duenio, propia).stream()
                .filter(m -> "LIBRE".equals(m.get("estado")) || "PROPIA".equals(m.get("estado")))
                .filter(m -> (Integer) m.get("capacidad") >= personas)
                .sorted(Comparator.comparingInt(m -> (Integer) m.get("capacidad")))
                .map(m -> (Integer) m.get("id"))
                .toList();
    }

    // =====================================================================
    // Disponibilidad
    // =====================================================================

    /** Para una fecha: cuantas mesas quedan libres en cada franja para ese numero de personas. */
    @Transactional
    public List<Map<String, Object>> horariosDe(LocalDate fecha, int personas, String duenio, Integer propia) {
        List<Map<String, Object>> salida = new ArrayList<>();
        boolean bloqueada = bloqueos.existsByFecha(fecha);
        List<Reserva> delDia = activasDe(fecha);
        for (LocalTime hora : franjas()) {
            List<Integer> libres = bloqueada ? List.of() : mesasQueAlcanzan(fecha, hora, personas, duenio, propia);
            int ocupadas = delDia.stream().filter(r -> seEmpalman(hora, r.getHora()))
                    .mapToInt(Reserva::getPersonas).sum();
            boolean cabeAforo = ocupadas + personas <= config.numero(Config.AFORO, 120);
            boolean pasada = fecha.isEqual(LocalDate.now()) && hora.isBefore(LocalTime.now());
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("hora", hora.toString());
            v.put("fin", hora.plusMinutes(config.duracion()).toString());
            v.put("mesasLibres", libres.size());
            v.put("disponible", !libres.isEmpty() && cabeAforo && !pasada && !bloqueada);
            v.put("motivo", bloqueada ? "El restaurante no abre ese dia"
                    : pasada ? "Esa hora ya paso"
                    : libres.isEmpty() ? "No quedan mesas para ese grupo"
                    : !cabeAforo ? "El restaurante ya esta a su maxima capacidad a esa hora" : null);
            salida.add(v);
        }
        return salida;
    }

    /** Estado de cada dia del mes, para pintar el calendario. */
    @Transactional
    public List<Map<String, Object>> dias(int anio, int mes, int personas) {
        YearMonth ym = YearMonth.of(anio, mes);
        LocalDate hoy = LocalDate.now();
        LocalDate ultimo = hoy.plusDays(config.numero(Config.DIAS_VISTA, 60));
        Set<LocalDate> bloqueadas = new HashSet<>();
        bloqueos.findByFechaBetween(ym.atDay(1), ym.atEndOfMonth()).forEach(b -> bloqueadas.add(b.getFecha()));
        List<Map<String, Object>> salida = new ArrayList<>();
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate fecha = ym.atDay(d);
            String estado;
            if (fecha.isBefore(hoy)) {
                estado = "PASADO";
            } else if (bloqueadas.contains(fecha)) {
                estado = "BLOQUEADO";
            } else if (fecha.isAfter(ultimo)) {
                estado = "LEJANO";
            } else {
                long libres = horariosDe(fecha, personas, "", null).stream()
                        .filter(h -> Boolean.TRUE.equals(h.get("disponible"))).count();
                long total = franjas().size();
                estado = libres == 0 ? "OCUPADO" : libres < total ? "PARCIAL" : "LIBRE";
            }
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("fecha", fecha.toString());
            v.put("estado", estado);
            salida.add(v);
        }
        return salida;
    }

    // =====================================================================
    // Apartado temporal
    // =====================================================================

    private void limpiarApartados() {
        apartados.borrarVencidos(LocalDateTime.now());
    }

    /** Aparta una mesa unos minutos mientras la persona termina de reservar. */
    @Transactional
    public Map<String, Object> apartar(Integer idMesa, LocalDate fecha, LocalTime hora, String duenio) {
        limpiarApartados();
        apartados.borrarDe(duenio); // cada quien aparta una sola mesa a la vez
        Mesa mesa = mesas.findById(idMesa)
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "Esa mesa no existe"));
        if (!Boolean.TRUE.equals(mesa.getActiva())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Esa mesa esta fuera de servicio");
        }
        boolean ocupada = croquis(fecha, hora, duenio, null).stream()
                .anyMatch(m -> m.get("id").equals(idMesa) && !"LIBRE".equals(m.get("estado")));
        if (ocupada) {
            throw new ErrorApp(HttpStatus.CONFLICT,
                    "Alguien acaba de quedarse con esa mesa. Elige otra en el croquis.");
        }
        ApartadoMesa a = new ApartadoMesa();
        a.setIdMesa(idMesa);
        a.setFecha(fecha);
        a.setHora(hora);
        a.setDuenio(duenio);
        a.setExpira(LocalDateTime.now().plusMinutes(config.numero(Config.APARTADO, 10)));
        apartados.save(a);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("idMesa", idMesa);
        r.put("expira", a.getExpira().toString());
        r.put("minutos", config.numero(Config.APARTADO, 10));
        return r;
    }

    @Transactional
    public void soltar(String duenio) {
        apartados.borrarDe(duenio);
    }

    // =====================================================================
    // Crear y editar reservas
    // =====================================================================

    @Transactional
    public Map<String, Object> crear(ReservaPeticion p, Persona persona, Usuario empleado, String duenio) {
        Reserva r = new Reserva();
        r.setPersona(persona);
        r.setRegistradaPor(empleado);
        r.setEstado(estado(CONFIRMADA));
        r.setCodigo(codigoNuevo());
        llenar(r, p, duenio, empleado != null);
        reservas.save(r);
        apartados.borrarDe(duenio);
        avisar(r, "Reserva confirmada",
                "Tu reserva quedo confirmada.\n\n" + resumenTexto(r)
                        + "\nCodigo de confirmacion: " + r.getCodigo()
                        + "\n\nMuestralo al llegar. Si no puedes venir, cancelala desde la pagina.");
        return vista(r, true);
    }

    @Transactional
    public Map<String, Object> actualizar(Integer id, ReservaPeticion p, Persona quien, boolean esPersonal, String duenio) {
        Reserva r = buscar(id, quien, esPersonal);
        if (!ACTIVOS.contains(r.getEstado().getId())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Esta reserva ya no se puede cambiar");
        }
        if (!esPersonal) {
            long horas = ChronoUnit.HOURS.between(LocalDateTime.now(), r.getFecha().atTime(r.getHora()));
            int limite = config.numero(Config.HORAS_CAMBIO, 4);
            if (horas < limite) {
                throw new ErrorApp(HttpStatus.CONFLICT, "Faltan menos de " + limite
                        + " horas para tu reserva: ya no se puede cambiar desde la pagina. Llama al restaurante.");
            }
        }
        llenar(r, p, duenio, esPersonal);
        reservas.save(r);
        apartados.borrarDe(duenio);
        avisar(r, "Reserva modificada", "Tu reserva cambio.\n\n" + resumenTexto(r));
        return vista(r, true);
    }

    /** Pasa los datos de la peticion a la reserva y revisa todas las reglas. */
    private void llenar(Reserva r, ReservaPeticion p, String duenio, boolean esPersonal) {
        LocalDate fecha = fecha(p.getFecha());
        LocalTime hora = hora(p.getHora());
        int personas = p.getPersonas() == null ? 0 : p.getPersonas();
        if (personas < 1) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Indica cuantas personas van a venir");
        }
        LocalDateTime cuando = fecha.atTime(hora);
        if (cuando.isBefore(LocalDateTime.now()) && !esPersonal) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Esa fecha y hora ya pasaron");
        }
        if (bloqueos.existsByFecha(fecha) && !esPersonal) {
            throw new ErrorApp(HttpStatus.CONFLICT, "El restaurante no abre ese dia");
        }
        if (!franjas().contains(hora) && !esPersonal) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Elige uno de los horarios disponibles");
        }
        if (p.getIdMesa() == null) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Elige tu mesa en el croquis");
        }
        Mesa mesa = mesas.findById(p.getIdMesa())
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "Esa mesa no existe"));
        if (!Boolean.TRUE.equals(mesa.getActiva())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Esa mesa esta fuera de servicio");
        }
        // Regla original de la base de datos: no se puede sentar mas gente de la que cabe
        if (personas > mesa.getCapacidad()) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "La mesa " + mesa.getId() + " es para "
                    + mesa.getCapacidad() + " personas y ustedes son " + personas + ". Elige una mas grande.");
        }
        // Regla original: dos reservas activas no pueden empalmarse en la misma mesa
        boolean ocupada = activasDe(fecha).stream()
                .filter(o -> o.getMesa().getId().equals(mesa.getId()))
                .filter(o -> r.getId() == null || !o.getId().equals(r.getId()))
                .anyMatch(o -> seEmpalman(hora, o.getHora()));
        if (ocupada) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Esa mesa ya esta reservada a esa hora. Elige otra.");
        }
        apartados.findByIdMesaAndFechaAndHora(mesa.getId(), fecha, hora)
                .filter(a -> a.getExpira().isAfter(LocalDateTime.now()) && !a.getDuenio().equals(duenio))
                .ifPresent(a -> {
                    throw new ErrorApp(HttpStatus.CONFLICT, "Alguien mas esta reservando esa mesa en este momento");
                });
        if (!esPersonal) {
            int ocupadas = activasDe(fecha).stream()
                    .filter(o -> r.getId() == null || !o.getId().equals(r.getId()))
                    .filter(o -> seEmpalman(hora, o.getHora()))
                    .mapToInt(Reserva::getPersonas).sum();
            if (ocupadas + personas > config.numero(Config.AFORO, 120)) {
                throw new ErrorApp(HttpStatus.CONFLICT,
                        "El restaurante ya esta a su maxima capacidad a esa hora. Elige otro horario.");
            }
        }
        r.setFecha(fecha);
        r.setHora(hora);
        r.setPersonas(personas);
        r.setMesa(mesa);
        if (p.getOcasion() != null) r.setOcasion(p.getOcasion().isBlank() ? null : p.getOcasion().trim());
        if (p.getNotas() != null) r.setNotas(p.getNotas().isBlank() ? null : p.getNotas().trim());
    }

    // =====================================================================
    // Ciclo de vida
    // =====================================================================

    @Transactional
    public Map<String, Object> cancelar(Integer id, Persona quien, boolean esPersonal) {
        Reserva r = buscar(id, quien, esPersonal);
        if (!ACTIVOS.contains(r.getEstado().getId())) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Esta reserva ya no esta activa");
        }
        if (!esPersonal) {
            long horas = ChronoUnit.HOURS.between(LocalDateTime.now(), r.getFecha().atTime(r.getHora()));
            int limite = config.numero(Config.HORAS_CAMBIO, 4);
            if (horas < limite) {
                throw new ErrorApp(HttpStatus.CONFLICT, "Faltan menos de " + limite
                        + " horas: llama al restaurante para cancelar.");
            }
        }
        r.setEstado(estado(CANCELADA));
        reservas.save(r);
        avisar(r, "Reserva cancelada", "Tu reserva del " + r.getFecha() + " a las " + r.getHora() + " quedo cancelada.");
        return vista(r, true);
    }

    /** Cambios de estado que hace el personal: llegada, salida o no se presento. */
    @Transactional
    public Map<String, Object> cambiarEstado(Integer id, String nuevo) {
        Reserva r = reservas.findById(id)
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "No existe esa reserva"));
        switch (nuevo) {
            case EN_MESA -> {
                r.setEstado(estado(EN_MESA));
                r.setHoraLlegada(LocalDateTime.now());
            }
            case FINALIZADA -> {
                r.setEstado(estado(FINALIZADA));
                r.setHoraSalida(LocalDateTime.now());
            }
            case NO_SE_PRESENTO -> r.setEstado(estado(NO_SE_PRESENTO));
            case CANCELADA -> r.setEstado(estado(CANCELADA));
            case CONFIRMADA -> r.setEstado(estado(CONFIRMADA));
            default -> throw new ErrorApp(HttpStatus.BAD_REQUEST, "Estado no valido");
        }
        reservas.save(r);
        return vista(r, true);
    }

    /** Cambiar a un cliente de mesa desde la vista de piso. */
    @Transactional
    public Map<String, Object> mover(Integer id, Integer idMesa) {
        Reserva r = reservas.findById(id)
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "No existe esa reserva"));
        Mesa mesa = mesas.findById(idMesa)
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "Esa mesa no existe"));
        boolean ocupada = activasDe(r.getFecha()).stream()
                .filter(o -> !o.getId().equals(r.getId()) && o.getMesa().getId().equals(idMesa))
                .anyMatch(o -> seEmpalman(r.getHora(), o.getHora()));
        if (ocupada) {
            throw new ErrorApp(HttpStatus.CONFLICT, "Esa mesa ya esta ocupada a esa hora");
        }
        if (r.getPersonas() > mesa.getCapacidad()) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST,
                    "En la mesa " + idMesa + " caben " + mesa.getCapacidad() + " personas y son " + r.getPersonas());
        }
        r.setMesa(mesa);
        reservas.save(r);
        return vista(r, true);
    }

    // =====================================================================
    // Consultas
    // =====================================================================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mias(Persona persona) {
        return reservas.findByPersona_IdOrderByFechaDescHoraDesc(persona.getId()).stream()
                .map(r -> vista(r, true)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> una(Integer id, Persona quien, boolean esPersonal) {
        return vista(buscar(id, quien, esPersonal), true);
    }

    /** Agenda de un dia para el personal, en orden de hora. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> agenda(LocalDate fecha) {
        return reservas.findByFecha(fecha).stream()
                .sorted(Comparator.comparing(Reserva::getHora).thenComparing(r -> r.getMesa().getId()))
                .map(r -> vista(r, true)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> porCodigo(String codigo) {
        Reserva r = reservas.findByCodigo(codigo.trim().toUpperCase())
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "No encontramos ninguna reserva con ese codigo"));
        return vista(r, true);
    }

    public List<Zona> zonas() {
        return zonas.findAll().stream().sorted(Comparator.comparing(Zona::getId)).toList();
    }

    // =====================================================================
    // Lista de espera
    // =====================================================================

    @Transactional
    public Map<String, Object> anotarEnEspera(Persona persona, ReservaPeticion p) {
        ListaEspera e = new ListaEspera();
        e.setPersona(persona);
        e.setFecha(fecha(p.getFecha()));
        e.setHora(hora(p.getHora()));
        e.setPersonas(p.getPersonas() == null ? 2 : p.getPersonas());
        espera.save(e);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mensaje", "Te anotamos en la lista de espera. Si se libera una mesa te avisamos.");
        return r;
    }

    // =====================================================================
    // Ayudas
    // =====================================================================

    private Reserva buscar(Integer id, Persona quien, boolean esPersonal) {
        Reserva r = reservas.findById(id)
                .orElseThrow(() -> new ErrorApp(HttpStatus.NOT_FOUND, "No existe esa reserva"));
        if (!esPersonal && (quien == null || !r.getPersona().getId().equals(quien.getId()))) {
            throw new ErrorApp(HttpStatus.FORBIDDEN, "Esa reserva no es tuya");
        }
        return r;
    }

    public EstadoReserva estado(String id) {
        return estados.findById(id)
                .orElseThrow(() -> new ErrorApp(HttpStatus.SERVICE_UNAVAILABLE, "Falta el estado " + id));
    }

    public Map<String, Object> vista(Reserva r, boolean conContacto) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", r.getId());
        v.put("fecha", r.getFecha().toString());
        v.put("hora", r.getHora().toString());
        v.put("horaFin", r.getHora().plusMinutes(config.duracion()).toString());
        v.put("personas", r.getPersonas());
        v.put("estado", r.getEstado().getId());
        v.put("estadoNombre", r.getEstado().getNombre());
        v.put("codigo", r.getCodigo());
        v.put("ocasion", r.getOcasion());
        v.put("notas", r.getNotas());
        v.put("idMesa", r.getMesa().getId());
        v.put("capacidadMesa", r.getMesa().getCapacidad());
        v.put("zona", r.getMesa().getZona() == null ? null : r.getMesa().getZona().getNombre());
        v.put("cliente", r.getPersona().getNombre());
        v.put("registradaPorEmpleado", r.getRegistradaPor() != null);
        v.put("horaLlegada", r.getHoraLlegada() == null ? null : r.getHoraLlegada().toString());
        v.put("horaSalida", r.getHoraSalida() == null ? null : r.getHoraSalida().toString());
        LocalDateTime cuando = r.getFecha().atTime(r.getHora());
        v.put("pasada", cuando.plusMinutes(config.duracion()).isBefore(LocalDateTime.now()));
        boolean activa = ACTIVOS.contains(r.getEstado().getId());
        long horas = ChronoUnit.HOURS.between(LocalDateTime.now(), cuando);
        v.put("puedeCambiar", activa && horas >= config.numero(Config.HORAS_CAMBIO, 4));
        // Para la agenda del personal: cliente que ya deberia haber llegado
        v.put("tarde", CONFIRMADA.equals(r.getEstado().getId())
                && LocalDateTime.now().isAfter(cuando.plusMinutes(config.numero(Config.TOLERANCIA, 20))));
        if (conContacto) {
            v.put("telefono", r.getPersona().getTelefono());
            v.put("correo", r.getPersona().getCorreo());
        }
        return v;
    }

    private String resumenTexto(Reserva r) {
        return "Fecha: " + r.getFecha() + "\nHora: " + r.getHora() + "\nPersonas: " + r.getPersonas()
                + "\nMesa: " + r.getMesa().getId()
                + (r.getMesa().getZona() == null ? "" : " (" + r.getMesa().getZona().getNombre() + ")");
    }

    /** El correo es un aviso: si falla el envio, la reserva se guarda igual. */
    private void avisar(Reserva r, String asunto, String texto) {
        String para = r.getPersona().getCorreo();
        if (para == null || para.isBlank()) return;
        try {
            String nombre = config.texto(Config.NOMBRE, "Restaurante");
            correo.enviar(para, nombre + " - " + asunto,
                    "Hola " + r.getPersona().getNombre() + ",\n\n" + texto + "\n\n" + nombre);
        } catch (RuntimeException e) {
            System.out.println("No se pudo avisar por correo a " + para + ": " + e.getMessage());
        }
    }

    private String codigoNuevo() {
        String letras = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin letras que se confunden con numeros
        StringBuilder sb = new StringBuilder("R-");
        for (int i = 0; i < 6; i++) sb.append(letras.charAt(aleatorio.nextInt(letras.length())));
        return sb.toString();
    }

    public LocalDate fecha(String texto) {
        try {
            return LocalDate.parse(texto.trim());
        } catch (RuntimeException e) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "La fecha debe tener el formato AAAA-MM-DD");
        }
    }

    public LocalTime hora(String texto) {
        try {
            return LocalTime.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "La hora debe tener el formato HH:mm");
        }
    }

    /** Persona por correo, o una nueva. Sirve para reservas que registra el personal. */
    @Transactional
    public Persona personaPara(String nombre, String telefono, String correoTexto) {
        if (nombre == null || nombre.isBlank()) {
            throw new ErrorApp(HttpStatus.BAD_REQUEST, "Escribe el nombre de quien reserva");
        }
        Persona p = null;
        if (correoTexto != null && !correoTexto.isBlank()) {
            p = personas.findFirstByCorreoIgnoreCase(correoTexto.trim()).orElse(null);
        }
        if (p == null) p = new Persona();
        p.setNombre(nombre.trim());
        if (telefono != null && !telefono.isBlank()) p.setTelefono(telefono.trim());
        if (correoTexto != null && !correoTexto.isBlank()) p.setCorreo(correoTexto.trim());
        return personas.save(p);
    }
}
