package com.example.reservas.config;

import com.example.reservas.modelo.*;
import com.example.reservas.repositorio.*;
import com.example.reservas.servicio.Config;
import com.example.reservas.servicio.Contrasenas;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deja la base lista la primera vez que arranca Spring: catalogos, configuracion del
 * restaurante, el croquis con sus zonas y mesas, la carta, las cuentas y unas reservas
 * de ejemplo para que la demostracion no se vea vacia.
 *
 * Todo esto se crea SOLO si no existe. Si ya hay datos, no toca nada.
 */
@Component
public class CargaDatos implements ApplicationRunner {

    private final RolRepositorio roles;
    private final OrigenCuentaRepositorio origenes;
    private final EstadoReservaRepositorio estadosReserva;
    private final EstadoPedidoRepositorio estadosPedido;
    private final CategoriaPlatilloRepositorio categorias;
    private final ParametroRepositorio parametros;
    private final ZonaRepositorio zonas;
    private final MesaRepositorio mesas;
    private final PlatilloRepositorio platillos;
    private final PersonaRepositorio personas;
    private final UsuarioRepositorio usuarios;
    private final ReservaRepositorio reservas;
    private final PedidoRepositorio pedidos;
    private final Contrasenas contrasenas;
    private final String correoAdmin;
    private final String claveAdmin;
    private final boolean conEjemplos;
    private final Random azar = new Random(7);

    public CargaDatos(RolRepositorio roles, OrigenCuentaRepositorio origenes,
                      EstadoReservaRepositorio estadosReserva, EstadoPedidoRepositorio estadosPedido,
                      CategoriaPlatilloRepositorio categorias, ParametroRepositorio parametros,
                      ZonaRepositorio zonas, MesaRepositorio mesas, PlatilloRepositorio platillos,
                      PersonaRepositorio personas, UsuarioRepositorio usuarios,
                      ReservaRepositorio reservas, PedidoRepositorio pedidos, Contrasenas contrasenas,
                      @Value("${datos.admin-correo:admin@restaurante.local}") String correoAdmin,
                      @Value("${datos.admin-password:Admin1234}") String claveAdmin,
                      @Value("${datos.ejemplos:true}") boolean conEjemplos) {
        this.roles = roles;
        this.origenes = origenes;
        this.estadosReserva = estadosReserva;
        this.estadosPedido = estadosPedido;
        this.categorias = categorias;
        this.parametros = parametros;
        this.zonas = zonas;
        this.mesas = mesas;
        this.platillos = platillos;
        this.personas = personas;
        this.usuarios = usuarios;
        this.reservas = reservas;
        this.pedidos = pedidos;
        this.contrasenas = contrasenas;
        this.correoAdmin = correoAdmin;
        this.claveAdmin = claveAdmin;
        this.conEjemplos = conEjemplos;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        catalogos();
        configuracion();
        croquis();
        carta();
        Usuario admin = administrador();
        if (conEjemplos) {
            cuentasDePrueba();
            reservasDeEjemplo(admin);
        }
        System.out.println("Datos listos. Administrador: " + correoAdmin);
    }

    // =====================================================================
    // Catalogos (los mismos valores que trae el script original)
    // =====================================================================

    private void catalogos() {
        rol("CLIENTE", "Cliente");
        rol("EMPLEADO", "Empleado");
        rol("ADMINISTRADOR", "Administrador");

        origen("LOCAL", "Cuenta del sistema");
        origen("GOOGLE", "Cuenta de Google");

        estadoReserva("CONFIRMADA", "Confirmada");
        estadoReserva("EN_MESA", "En mesa");
        estadoReserva("FINALIZADA", "Finalizada");
        estadoReserva("CANCELADA", "Cancelada");
        estadoReserva("NO_SE_PRESENTO", "No se presento");

        estadoPedido("PENDIENTE", "Pendiente");
        estadoPedido("PREPARANDO", "Preparando");
        estadoPedido("SERVIDO", "Servido");
        estadoPedido("CANCELADO", "Cancelado");

        categoria("ENTRADAS", "Entradas");
        categoria("PLATOS_FUERTES", "Platos fuertes");
        categoria("POSTRES", "Postres");
        categoria("BEBIDAS", "Bebidas");
    }

    private void rol(String id, String nombre) {
        if (!roles.existsById(id)) roles.save(new Rol(id, nombre));
    }

    private void origen(String id, String nombre) {
        if (!origenes.existsById(id)) origenes.save(new OrigenCuenta(id, nombre));
    }

    private void estadoReserva(String id, String nombre) {
        if (!estadosReserva.existsById(id)) estadosReserva.save(new EstadoReserva(id, nombre));
    }

    private void estadoPedido(String id, String nombre) {
        if (!estadosPedido.existsById(id)) estadosPedido.save(new EstadoPedido(id, nombre));
    }

    private void categoria(String id, String nombre) {
        if (!categorias.existsById(id)) categorias.save(new CategoriaPlatillo(id, nombre));
    }

    // =====================================================================
    // Configuracion del restaurante
    // =====================================================================

    private void configuracion() {
        parametro(Config.APERTURA, "13:00", "Primera hora a la que se puede reservar.");
        parametro(Config.CIERRE, "23:00", "Hora a la que cierra: la ultima reserva debe terminar antes.");
        parametro(Config.DURACION, "105", "Minutos que una reserva ocupa la mesa.");
        parametro(Config.PASO, "30", "Cada cuantos minutos empieza un horario (13:00, 13:30...).");
        parametro(Config.AFORO, "90", "Maximo de comensales al mismo tiempo en todo el restaurante.");
        parametro(Config.TOLERANCIA, "20", "Minutos de tolerancia antes de marcar que el cliente no llego.");
        parametro(Config.APARTADO, "10", "Minutos que una mesa queda apartada mientras el cliente reserva.");
        parametro(Config.DIAS_VISTA, "60", "Con cuantos dias de anticipacion se puede reservar.");
        parametro(Config.HORAS_CAMBIO, "4", "Horas antes de la reserva en que el cliente aun puede cambiarla.");
        parametro(Config.CODIGO_EMPLEADO, "EMP-DEMO26", "Codigo que se pide para registrarse como empleado.");
        parametro(Config.NOMBRE, "Mesa Nuestra", "Nombre del restaurante.");
        parametro(Config.FRASE, "Cocina mexicana de temporada", "Frase que aparece en la portada.");
        parametro(Config.DESCRIPCION,
                "Un restaurante de barrio con cocina de temporada, pan hecho en casa y mezcales de pueblo. "
                        + "Reserva tu mesa, elige exactamente dónde quieres sentarte y deja tu pedido listo.",
                "Texto de presentacion de la portada.");
        parametro(Config.DIRECCION, "Av. Reforma 120, Centro, Tecámac, Estado de México",
                "Direccion que se muestra al cliente.");
        parametro(Config.TELEFONO, "5591234567", "Telefono para dudas y cancelaciones de ultima hora.");
        parametro(Config.FOTOS, "", "Direcciones de las fotos de la portada, separadas por comas.");
    }

    private void parametro(String id, String valor, String descripcion) {
        if (!parametros.existsById(id)) parametros.save(new Parametro(id, valor, descripcion));
    }

    // =====================================================================
    // Croquis: zonas y mesas (las medidas estan en metros)
    // =====================================================================

    private void croquis() {
        if (zonas.count() > 0) return;
        Zona salon = zona("Salón principal", 0, 0, 11, 9, "#F7EFE1", "SALA", true,
                "El área grande, junto a los ventanales.");
        Zona terraza = zona("Terraza", 11.5, 0, 8, 5.5, "#E7F1E3", "SALA", true,
                "Al aire libre, con plantas. Se puede fumar.");
        Zona barra = zona("Barra", 11.5, 6, 8, 3, "#EFE8F3", "SALA", true,
                "Mesas altas frente a la cocina. Ideales para dos personas.");
        Zona privado = zona("Salón privado", 0, 9.5, 7, 4, "#F5E7E4", "SALA", true,
                "Cuarto aparte para grupos y comidas de trabajo.");
        zona("Cocina", 7.5, 9.5, 5, 4, "#E6E6E6", "SERVICIO", false, "Área de servicio.");
        zona("Entrada y sanitarios", 13, 9.5, 6.5, 4, "#E6E6E6", "SERVICIO", false, "Área de servicio.");

        // Salón principal: cuatro columnas por tres filas
        int[] capacidadesSalon = {2, 4, 4, 2, 4, 6, 6, 4, 4, 4, 2, 4};
        double[] equis = {1.6, 4.2, 6.8, 9.4};
        double[] yes = {1.6, 4.5, 7.4};
        int i = 0;
        for (double y : yes) {
            for (double x : equis) {
                int capacidad = capacidadesSalon[i];
                mesa(i + 1, capacidad, salon, x, y, capacidad > 4 ? "CUADRADA" : "REDONDA",
                        y == yes[0] ? "ventana" : null);
                i++;
            }
        }
        // Terraza
        int[] capacidadesTerraza = {2, 4, 4, 2, 4, 6};
        double[] equisT = {13.0, 15.5, 18.0};
        double[] yesT = {1.5, 4.0};
        int j = 0;
        for (double y : yesT) {
            for (double x : equisT) {
                mesa(21 + j, capacidadesTerraza[j], terraza, x, y,
                        capacidadesTerraza[j] > 4 ? "CUADRADA" : "REDONDA", "terraza,fumar");
                j++;
            }
        }
        // Barra: mesas altas de dos
        double[] equisB = {12.6, 14.6, 16.6, 18.6};
        for (int k = 0; k < equisB.length; k++) {
            mesa(31 + k, 2, barra, equisB[k], 7.5, "CUADRADA", "barra,alta");
        }
        // Salón privado
        mesa(41, 8, privado, 2.0, 11.5, "CUADRADA", "privado,accesible");
        mesa(42, 10, privado, 5.0, 11.5, "CUADRADA", "privado");
    }

    private Zona zona(String nombre, double x, double y, double ancho, double alto,
                      String color, String tipo, boolean reservable, String descripcion) {
        Zona z = new Zona();
        z.setNombre(nombre);
        z.setX(x);
        z.setY(y);
        z.setAncho(ancho);
        z.setAlto(alto);
        z.setColor(color);
        z.setTipo(tipo);
        z.setReservable(reservable);
        z.setDescripcion(descripcion);
        return zonas.save(z);
    }

    private void mesa(int id, int capacidad, Zona zona, double x, double y, String forma, String etiquetas) {
        if (mesas.existsById(id)) return;
        Mesa m = new Mesa();
        m.setId(id);
        m.setCapacidad(capacidad);
        m.setZona(zona);
        m.setX(x);
        m.setY(y);
        m.setForma(forma);
        m.setEtiquetas(etiquetas);
        m.setActiva(true);
        mesas.save(m);
    }

    // =====================================================================
    // Carta
    // =====================================================================

    private void carta() {
        if (platillos.count() > 0) return;
        platillo("Guacamole con chicharrón de queso", "Aguacate, cilantro, chile serrano y totopos de maíz azul.",
                "ENTRADAS", 135, "vegetariano", "lacteos");
        platillo("Sopa de tortilla", "Caldillo de jitomate rostizado, aguacate, crema y chile pasilla.",
                "ENTRADAS", 110, null, "lacteos");
        platillo("Esquites con epazote", "Grano tierno, mayonesa de chile de árbol y queso cotija.",
                "ENTRADAS", 85, "vegetariano,picante", "lacteos,huevo");
        platillo("Tostadas de atún", "Atún fresco, chile morita, aguacate y ajonjolí.",
                "ENTRADAS", 160, "picante", "pescado,ajonjoli");
        platillo("Ensalada de betabel", "Betabel rostizado, queso de cabra, nuez y vinagreta de naranja.",
                "ENTRADAS", 125, "vegetariano,sin gluten", "lacteos,nueces");
        platillo("Queso fundido con chorizo", "Queso Oaxaca, chorizo de Toluca y tortillas de maíz.",
                "ENTRADAS", 145, null, "lacteos");
        platillo("Crema de calabaza", "Calabaza de Castilla, canela y pepitas tostadas.",
                "ENTRADAS", 95, "vegetariano", "lacteos");

        platillo("Mole de olla", "Res, chayote, elote y chile guajillo. Se sirve con arroz.",
                "PLATOS_FUERTES", 265, null, null);
        platillo("Enchiladas de mole poblano", "Pollo deshebrado, ajonjolí y crema.",
                "PLATOS_FUERTES", 215, null, "lacteos,ajonjoli,nueces");
        platillo("Pescado a la talla", "Filete en adobo de chile guajillo, arroz y verduras.",
                "PLATOS_FUERTES", 320, "picante", "pescado");
        platillo("Arrachera al carbón", "300 g con cebollitas, frijoles charros y tortillas.",
                "PLATOS_FUERTES", 395, null, null);
        platillo("Chiles en nogada", "De temporada. Carne de cerdo, frutas y nogada de nuez de Castilla.",
                "PLATOS_FUERTES", 340, "temporada", "lacteos,nueces");
        platillo("Pechuga en pipián verde", "Pipián de pepita, verdolagas y arroz blanco.",
                "PLATOS_FUERTES", 255, "sin gluten", "pepita");
        platillo("Costilla de cerdo en pasilla", "Cocida ocho horas, puré de papa y chile pasilla.",
                "PLATOS_FUERTES", 310, null, "lacteos");
        platillo("Tacos de carnitas", "Cuatro tacos de maciza y cuero, con salsa verde.",
                "PLATOS_FUERTES", 195, null, null);
        platillo("Risotto de huitlacoche", "Arroz arborio, huitlacoche, queso de rancho y epazote.",
                "PLATOS_FUERTES", 240, "vegetariano", "lacteos");
        platillo("Verduras al comal", "Calabaza, nopal, cebolla, elote y salsa macha. Sin productos de origen animal.",
                "PLATOS_FUERTES", 180, "vegano,picante", "cacahuate,ajonjoli");

        platillo("Flan de cajeta", "Con nuez caramelizada.", "POSTRES", 95, "vegetariano", "lacteos,huevo,nueces");
        platillo("Pastel de elote", "Tibio, con helado de vainilla.", "POSTRES", 105, "vegetariano",
                "lacteos,huevo,gluten");
        platillo("Nieve de mamey", "Hecha en la casa.", "POSTRES", 75, "vegetariano,sin gluten", "lacteos");
        platillo("Churros con chocolate", "Seis churros con chocolate de metate.", "POSTRES", 110,
                "vegetariano", "gluten,lacteos");
        platillo("Arroz con leche", "Con canela y pasas.", "POSTRES", 80, "vegetariano,sin gluten", "lacteos");

        platillo("Agua de jamaica", "Jarra de un litro, sin azúcar añadida.", "BEBIDAS", 70, "vegano", null);
        platillo("Limonada con chia", "Vaso de 500 ml.", "BEBIDAS", 55, "vegano", null);
        platillo("Cafe de olla", "Con piloncillo y canela.", "BEBIDAS", 48, "vegano", null);
        platillo("Mezcal espadin", "Caballito de 45 ml, de Santiago Matatlán.", "BEBIDAS", 130, null, null);
        platillo("Cerveza artesanal", "Botella de 355 ml, estilo del mes.", "BEBIDAS", 85, null, "gluten");
        platillo("Refresco", "Vidrio de 355 ml.", "BEBIDAS", 42, null, null);
    }

    private void platillo(String nombre, String descripcion, String categoria, double precio,
                          String etiquetas, String alergenos) {
        Platillo p = new Platillo();
        p.setNombre(nombre);
        p.setDescripcion(descripcion);
        p.setCategoria(categorias.findById(categoria).orElseThrow());
        p.setPrecio(precio);
        p.setEtiquetas(etiquetas);
        p.setAlergenos(alergenos);
        p.setActivo(true);
        platillos.save(p);
    }

    // =====================================================================
    // Cuentas
    // =====================================================================

    private Usuario administrador() {
        Usuario existente = usuarios.findByUsuarioIgnoreCase(correoAdmin).orElse(null);
        if (existente != null) return existente;
        if (usuarios.existsByRol_Id("ADMINISTRADOR")) {
            return usuarios.findAll().stream()
                    .filter(u -> "ADMINISTRADOR".equals(u.getRol().getId()))
                    .findFirst().orElse(null);
        }
        return cuenta("Administrador del restaurante", "5590000000", correoAdmin, claveAdmin, "ADMINISTRADOR");
    }

    private void cuentasDePrueba() {
        if (usuarios.count() > 1) return;
        cuenta("María Hernández", "5551112233", "mesero@restaurante.local", "Mesero1234", "EMPLEADO");
        cuenta("Noel Velasco", "5552223344", "cliente@restaurante.local", "Cliente1234", "CLIENTE");
        cuenta("Laura Mendoza", "5553334455", "laura@correo.local", "Cliente1234", "CLIENTE");
        cuenta("Ricardo Gómez", "5554445566", "ricardo@correo.local", "Cliente1234", "CLIENTE");
    }

    private Usuario cuenta(String nombre, String telefono, String correo, String clave, String rol) {
        if (usuarios.existsByUsuarioIgnoreCase(correo)) {
            return usuarios.findByUsuarioIgnoreCase(correo).orElseThrow();
        }
        Persona p = new Persona();
        p.setNombre(nombre);
        p.setTelefono(telefono);
        p.setCorreo(correo);
        personas.save(p);

        Usuario u = new Usuario();
        u.setPersona(p);
        u.setUsuario(correo);
        u.setContrasenaHash(contrasenas.cifrar(clave));
        u.setRol(roles.findById(rol).orElseThrow());
        u.setOrigen(origenes.findById("LOCAL").orElseThrow());
        u.setActivo(true);
        u.setDebeCambiarContrasena(false);
        return usuarios.save(u);
    }

    // =====================================================================
    // Reservas de ejemplo
    // =====================================================================

    private void reservasDeEjemplo(Usuario admin) {
        if (reservas.count() > 0) return;
        List<Persona> clientes = new ArrayList<>();
        for (String correo : List.of("cliente@restaurante.local", "laura@correo.local", "ricardo@correo.local")) {
            personas.findFirstByCorreoIgnoreCase(correo).ifPresent(clientes::add);
        }
        // Clientes sin cuenta: reservas que levanto el personal por telefono
        clientes.add(personaSuelta("Familia Pérez Luna", "5556667788"));
        clientes.add(personaSuelta("Sofía Ramírez", "5557778899"));
        clientes.add(personaSuelta("Jorge Castillo", "5558889900"));
        if (clientes.isEmpty()) return;

        List<Mesa> todas = mesas.findByActivaTrue();
        List<Platillo> carta = platillos.findAll();
        List<String> ocasiones = List.of("Cumpleaños", "Aniversario", "Comida de trabajo", "", "Reunión familiar");
        LocalTime[] horas = {LocalTime.of(13, 30), LocalTime.of(14, 0), LocalTime.of(15, 0),
                LocalTime.of(19, 0), LocalTime.of(20, 0), LocalTime.of(20, 30), LocalTime.of(21, 0)};

        // Historial: de hace 21 dias a ayer
        for (int dia = 21; dia >= 1; dia--) {
            LocalDate fecha = LocalDate.now().minusDays(dia);
            int cuantas = 2 + azar.nextInt(3);
            for (int n = 0; n < cuantas; n++) {
                String estado = azar.nextInt(10) < 8 ? "FINALIZADA"
                        : azar.nextBoolean() ? "CANCELADA" : "NO_SE_PRESENTO";
                Reserva r = reservaNueva(fecha, horas[azar.nextInt(horas.length)],
                        clientes.get(azar.nextInt(clientes.size())), todas, estado, ocasiones, admin);
                if (r != null && "FINALIZADA".equals(estado)) pedidoDeEjemplo(r, carta, "SERVIDO");
            }
        }
        // Hoy y los proximos dias: reservas confirmadas que si se pueden trabajar en la demo
        for (int dia = 0; dia <= 9; dia++) {
            LocalDate fecha = LocalDate.now().plusDays(dia);
            int cuantas = dia == 0 ? 5 : 1 + azar.nextInt(3);
            for (int n = 0; n < cuantas; n++) {
                Reserva r = reservaNueva(fecha, horas[azar.nextInt(horas.length)],
                        clientes.get(azar.nextInt(clientes.size())), todas, "CONFIRMADA", ocasiones, admin);
                if (r == null) continue;
                // Un par de mesas de hoy ya sentadas, para que la vista de piso tenga movimiento
                if (dia == 0 && r.getHora().isBefore(LocalTime.now()) && azar.nextBoolean()) {
                    r.setEstado(estadosReserva.findById("EN_MESA").orElseThrow());
                    r.setHoraLlegada(fecha.atTime(r.getHora()).plusMinutes(5));
                    reservas.save(r);
                    pedidoDeEjemplo(r, carta, azar.nextBoolean() ? "PREPARANDO" : "SERVIDO");
                } else if (dia == 0 && azar.nextBoolean()) {
                    pedidoDeEjemplo(r, carta, "PENDIENTE");
                }
            }
        }
        System.out.println("Reservas de ejemplo creadas: " + reservas.count());
    }

    /** Dos reservas se empalman si una empieza antes de que termine la otra. */
    private boolean seEmpalman(LocalTime a, LocalTime b) {
        int duracion = Integer.parseInt(parametros.findById(Config.DURACION)
                .map(Parametro::getValor).orElse("105"));
        return a.isBefore(b.plusMinutes(duracion)) && b.isBefore(a.plusMinutes(duracion));
    }

    private Persona personaSuelta(String nombre, String telefono) {
        Persona p = new Persona();
        p.setNombre(nombre);
        p.setTelefono(telefono);
        return personas.save(p);
    }

    /** Crea una reserva en una mesa que quede libre a esa hora; si no hay, no crea nada. */
    private Reserva reservaNueva(LocalDate fecha, LocalTime hora, Persona persona, List<Mesa> todas,
                                 String estado, List<String> ocasiones, Usuario admin) {
        int personasReserva = 2 + azar.nextInt(5);
        List<Reserva> delDia = reservas.findByFecha(fecha);
        // Se recorren en orden distinto cada vez para que las reservas se repartan entre las zonas
        List<Mesa> revueltas = new ArrayList<>(todas);
        java.util.Collections.shuffle(revueltas, azar);
        for (Mesa m : revueltas) {
            if (m.getCapacidad() < personasReserva) continue;
            boolean ocupada = delDia.stream()
                    .filter(o -> o.getMesa().getId().equals(m.getId()))
                    .filter(o -> List.of("CONFIRMADA", "EN_MESA", "FINALIZADA").contains(o.getEstado().getId()))
                    .anyMatch(o -> seEmpalman(hora, o.getHora()));
            if (ocupada) continue;
            Reserva r = new Reserva();
            r.setPersona(persona);
            r.setMesa(m);
            r.setFecha(fecha);
            r.setHora(hora);
            r.setPersonas(personasReserva);
            r.setEstado(estadosReserva.findById(estado).orElseThrow());
            r.setCodigo(codigo());
            String ocasion = ocasiones.get(azar.nextInt(ocasiones.size()));
            if (!ocasion.isBlank()) r.setOcasion(ocasion);
            if (azar.nextInt(4) == 0) r.setNotas("Una silla para bebé, por favor.");
            if (persona.getCorreo() == null) r.setRegistradaPor(admin); // la levanto el personal
            if ("FINALIZADA".equals(estado)) {
                r.setHoraLlegada(fecha.atTime(hora).plusMinutes(azar.nextInt(12)));
                r.setHoraSalida(fecha.atTime(hora).plusMinutes(80 + azar.nextInt(40)));
            }
            return reservas.save(r);
        }
        return null;
    }

    private void pedidoDeEjemplo(Reserva reserva, List<Platillo> carta, String estado) {
        if (carta.isEmpty()) return;
        Pedido p = new Pedido();
        p.setReserva(reserva);
        p.setEstado(estadosPedido.findById(estado).orElseThrow());
        int cuantos = 2 + azar.nextInt(3);
        for (int i = 0; i < cuantos; i++) {
            Platillo platillo = carta.get(azar.nextInt(carta.size()));
            PedidoDetalle d = new PedidoDetalle();
            d.setPlatillo(platillo);
            d.setCantidad(1 + azar.nextInt(2));
            d.setPrecioUnitario(platillo.getPrecio());
            if (azar.nextInt(5) == 0) d.setNotas("Sin cebolla");
            p.getLineas().add(d);
        }
        pedidos.save(p);
    }

    private String codigo() {
        String letras = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("R-");
        for (int i = 0; i < 6; i++) sb.append(letras.charAt(azar.nextInt(letras.length())));
        return sb.toString();
    }
}
