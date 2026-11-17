-- =====================================================================
--  SISTEMA DE RESERVAS Y PEDIDOS - ESTRUCTURA COMPLETA
--  PostgreSQL
--
--  Esta es la base que usa el sistema. Parte del script original
--  (01-estructura-original.sql) y le agrega lo que hacia falta para que
--  el cliente pueda ELEGIR SU MESA EN EL CROQUIS.
--
--  NO ES NECESARIO CORRER ESTE ARCHIVO: al arrancar Spring con
--  spring.jpa.hibernate.ddl-auto=update, las tablas se crean solas y
--  CargaDatos.java mete los catalogos, el croquis, la carta y las
--  cuentas de prueba. Este script esta aqui como documentacion y por si
--  se quiere armar la base a mano.
--
--  Cada bloque dice si la tabla viene del script original o es NUEVA, y
--  en las tablas originales se marcan las columnas agregadas.
-- =====================================================================

-- ---------------------------------------------------------------------
-- CATALOGOS (del script original, sin cambios)
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS roles (
    id_rol      VARCHAR(30) PRIMARY KEY,
    nombre_rol  VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS origenes_cuenta (
    id_origen_cuenta      VARCHAR(30) PRIMARY KEY,
    nombre_origen_cuenta  VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS estados_reserva (
    id_estado_reserva      VARCHAR(30) PRIMARY KEY,
    nombre_estado_reserva  VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS estados_pedido (
    id_estado_pedido      VARCHAR(30) PRIMARY KEY,
    nombre_estado_pedido  VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS categorias_platillo (
    id_categoria_platillo      VARCHAR(30) PRIMARY KEY,
    nombre_categoria_platillo  VARCHAR(50) NOT NULL
);

-- Configuracion del restaurante. Nada de esto esta escrito en el codigo:
-- horarios, duracion de la reserva, aforo, nombre, direccion, etc.
CREATE TABLE IF NOT EXISTS parametros (
    id_parametro           VARCHAR(60) PRIMARY KEY,
    valor_parametro        VARCHAR(500) NOT NULL,
    descripcion_parametro  VARCHAR(255)
);

-- ---------------------------------------------------------------------
-- ZONAS  (TABLA NUEVA)
-- Las areas del restaurante. Sus medidas estan en METROS y son las que
-- dibujan el fondo del croquis. Las de tipo SERVICIO (cocina, banos,
-- entrada) se ven en el plano pero no reciben mesas.
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS zonas (
    id_zona      SERIAL PRIMARY KEY,
    nombre_zona  VARCHAR(60) NOT NULL,
    x            DOUBLE PRECISION DEFAULT 0,   -- esquina de arriba a la izquierda
    y            DOUBLE PRECISION DEFAULT 0,
    ancho        DOUBLE PRECISION DEFAULT 6,
    alto         DOUBLE PRECISION DEFAULT 6,
    color        VARCHAR(9)   DEFAULT '#F7EFE1',
    tipo         VARCHAR(20)  DEFAULT 'SALA',   -- SALA | SERVICIO
    descripcion  VARCHAR(200),
    reservable   BOOLEAN      DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- PERSONAS (del script original + 2 columnas NUEVAS)
-- Sin telefono ni correo no hay forma de confirmar ni de avisar nada.
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS personas (
    id_persona        SERIAL PRIMARY KEY,
    nombre_persona    VARCHAR(100) NOT NULL,
    telefono_persona  VARCHAR(20),              -- NUEVA
    correo_persona    VARCHAR(120)              -- NUEVA
);

-- ---------------------------------------------------------------------
-- USUARIOS (del script original + 1 columna NUEVA)
-- La contrasena se guarda cifrada con BCrypt, no en SHA-256.
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS usuarios (
    id_usuario               SERIAL PRIMARY KEY,
    id_persona               INTEGER NOT NULL UNIQUE REFERENCES personas (id_persona),
    nombre_usuario           VARCHAR(120) NOT NULL UNIQUE,   -- es el correo
    contrasena_hash          VARCHAR(255) NOT NULL,
    id_rol                   VARCHAR(30) NOT NULL REFERENCES roles (id_rol),
    id_origen_cuenta         VARCHAR(30) NOT NULL REFERENCES origenes_cuenta (id_origen_cuenta),
    usuario_activo           BOOLEAN NOT NULL DEFAULT TRUE,
    debe_cambiar_contrasena  BOOLEAN NOT NULL DEFAULT FALSE, -- NUEVA (contrasena temporal)
    fecha_registro_usuario   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Sesiones con token. Spring ya no confia en el id que mande la pagina:
-- sabe quien eres por la huella del token.  (TABLA NUEVA)
CREATE TABLE IF NOT EXISTS sesiones (
    id          SERIAL PRIMARY KEY,
    token_hash  VARCHAR(100) NOT NULL UNIQUE,
    usuario_id  INTEGER NOT NULL,
    creada_en   TIMESTAMP,
    expira_en   TIMESTAMP
);

-- Registro a medias: alguien lleno el formulario y se le mando un codigo al
-- correo, pero la cuenta TODAVIA NO EXISTE. Se crea hasta que escribe el codigo,
-- y asi se comprueba que el correo es de verdad suyo.  (TABLA NUEVA)
CREATE TABLE IF NOT EXISTS solicitudes_registro (
    id_solicitud     SERIAL PRIMARY KEY,
    nombre           VARCHAR(100) NOT NULL,
    telefono         VARCHAR(20),
    correo           VARCHAR(120) NOT NULL,
    contrasena_hash  VARCHAR(255) NOT NULL,   -- ya cifrada: aqui nunca va la contrasena tal cual
    id_rol           VARCHAR(30) NOT NULL DEFAULT 'CLIENTE',
    codigo           VARCHAR(10) NOT NULL,
    expira           TIMESTAMP NOT NULL,
    enviado_en       TIMESTAMP NOT NULL DEFAULT NOW(),
    usado            BOOLEAN NOT NULL DEFAULT FALSE,
    intentos         INTEGER DEFAULT 0
);

-- Tramites ARCO que no se pueden deshacer, a medio hacer.  (TABLA NUEVA)
--
--   BAJA          -> un codigo al correo de la cuenta.
--   CAMBIO_CORREO -> un codigo al correo ACTUAL (para saber que lo pide el dueno)
--                    y, cuando ese se confirma, OTRO al correo NUEVO (para saber
--                    que ese correo existe y es suyo).
--
-- Mientras el tramite no se confirme completo, en la cuenta no cambia nada.
CREATE TABLE IF NOT EXISTS solicitudes_cuenta (
    id_solicitud_cuenta  SERIAL PRIMARY KEY,
    id_usuario           INTEGER NOT NULL,
    tipo                 VARCHAR(20) NOT NULL,   -- BAJA | CAMBIO_CORREO
    correo_nuevo         VARCHAR(120),           -- solo en CAMBIO_CORREO
    codigo_actual        VARCHAR(10) NOT NULL,   -- el que va al correo de hoy
    codigo_nuevo         VARCHAR(10),            -- el que va al correo nuevo
    actual_confirmado    BOOLEAN NOT NULL DEFAULT FALSE,
    expira               TIMESTAMP NOT NULL,
    enviado_en           TIMESTAMP NOT NULL DEFAULT NOW(),
    usado                BOOLEAN NOT NULL DEFAULT FALSE,
    intentos             INTEGER DEFAULT 0
);

-- Codigos de 6 digitos para recuperar la contrasena.  (TABLA NUEVA)
CREATE TABLE IF NOT EXISTS codigos_recuperacion (
    id_codigo   SERIAL PRIMARY KEY,
    id_usuario  INTEGER NOT NULL,
    codigo      VARCHAR(10) NOT NULL,
    expira      TIMESTAMP NOT NULL,
    usado       BOOLEAN NOT NULL DEFAULT FALSE,
    intentos    INTEGER DEFAULT 0
);

-- ---------------------------------------------------------------------
-- MESAS (del script original + 5 columnas NUEVAS)
-- Sin la posicion y la zona no se puede dibujar el croquis, que es lo
-- que permite que el cliente escoja donde sentarse.
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS mesas (
    id_mesa         INTEGER PRIMARY KEY,         -- el numero lo pone el administrador
    capacidad_mesa  INTEGER NOT NULL DEFAULT 4,
    mesa_activa     BOOLEAN NOT NULL DEFAULT TRUE,
    id_zona         INTEGER REFERENCES zonas (id_zona),  -- NUEVA
    posicion_x      DOUBLE PRECISION DEFAULT 1,          -- NUEVA (metros)
    posicion_y      DOUBLE PRECISION DEFAULT 1,          -- NUEVA (metros)
    forma_mesa      VARCHAR(20) DEFAULT 'REDONDA',       -- NUEVA: REDONDA | CUADRADA
    etiquetas_mesa  VARCHAR(200)                         -- NUEVA: ventana, accesible...
);

-- ---------------------------------------------------------------------
-- RESERVAS (del script original + 5 columnas NUEVAS)
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS reservas (
    id_reserva              SERIAL PRIMARY KEY,
    id_persona              INTEGER NOT NULL REFERENCES personas (id_persona),
    id_mesa                 INTEGER NOT NULL REFERENCES mesas (id_mesa),
    fecha_reserva           DATE NOT NULL,
    hora_reserva            TIME NOT NULL,
    cantidad_personas       INTEGER NOT NULL DEFAULT 2,
    id_estado_reserva       VARCHAR(30) NOT NULL REFERENCES estados_reserva (id_estado_reserva),
    id_usuario_registro     INTEGER REFERENCES usuarios (id_usuario),  -- vacio si la hizo el cliente
    fecha_registro_reserva  TIMESTAMP NOT NULL DEFAULT NOW(),
    codigo_confirmacion     VARCHAR(12),   -- NUEVA: lo que el cliente muestra al llegar
    ocasion_reserva         VARCHAR(60),   -- NUEVA: cumpleanos, aniversario...
    notas_reserva           VARCHAR(300),  -- NUEVA: alergias, silla para bebe...
    hora_llegada            TIMESTAMP,     -- NUEVA: a que hora llegaron de verdad
    hora_salida             TIMESTAMP      -- NUEVA: a que hora se fueron
);

-- Mesa que alguien esta eligiendo en este momento: queda apartada unos
-- minutos para que dos personas no se queden con la misma.  (TABLA NUEVA)
CREATE TABLE IF NOT EXISTS apartados_mesa (
    id_apartado  SERIAL PRIMARY KEY,
    id_mesa      INTEGER NOT NULL,
    fecha        DATE NOT NULL,
    hora         TIME NOT NULL,
    duenio       VARCHAR(100) NOT NULL,  -- quien la aparto (su navegador o su sesion)
    expira       TIMESTAMP NOT NULL
);

-- Dias en los que el restaurante no recibe reservas.  (TABLA NUEVA)
CREATE TABLE IF NOT EXISTS fechas_bloqueadas (
    id_fecha_bloqueada  SERIAL PRIMARY KEY,
    fecha               DATE NOT NULL UNIQUE,
    motivo              VARCHAR(200)
);

-- Cliente que queria mesa y no habia.  (TABLA NUEVA)
CREATE TABLE IF NOT EXISTS lista_espera (
    id_espera        SERIAL PRIMARY KEY,
    id_persona       INTEGER NOT NULL REFERENCES personas (id_persona),
    fecha            DATE NOT NULL,
    hora             TIME NOT NULL,
    personas         INTEGER NOT NULL DEFAULT 2,
    estado           VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_registro   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- CARTA Y PEDIDOS (del script original + columnas NUEVAS)
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS platillos (
    id_platillo            SERIAL PRIMARY KEY,
    nombre_platillo        VARCHAR(100) NOT NULL,
    descripcion_platillo   VARCHAR(255),
    id_categoria_platillo  VARCHAR(30) NOT NULL REFERENCES categorias_platillo (id_categoria_platillo),
    precio_platillo        DOUBLE PRECISION NOT NULL DEFAULT 0,
    imagen_platillo        VARCHAR(255),
    platillo_activo        BOOLEAN NOT NULL DEFAULT TRUE,
    etiquetas_platillo     VARCHAR(200),   -- NUEVA: vegetariano, picante, sin gluten
    alergenos_platillo     VARCHAR(200)    -- NUEVA: lacteos, gluten, nueces
);

CREATE TABLE IF NOT EXISTS pedidos (
    id_pedido              SERIAL PRIMARY KEY,
    id_reserva             INTEGER NOT NULL REFERENCES reservas (id_reserva),
    id_estado_pedido       VARCHAR(30) NOT NULL REFERENCES estados_pedido (id_estado_pedido),
    fecha_registro_pedido  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- CAMBIO respecto al script original: la linea tiene su PROPIO id en
-- lugar de usar la pareja (id_pedido, id_platillo) como llave primaria.
-- Asi se puede pedir dos veces el mismo platillo con notas distintas
-- (uno termino medio y otro bien cocido), que con la llave compuesta
-- era imposible.
CREATE TABLE IF NOT EXISTS pedido_detalle (
    id_detalle                INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_pedido                 INTEGER REFERENCES pedidos (id_pedido),
    id_platillo               INTEGER NOT NULL REFERENCES platillos (id_platillo),
    cantidad_platillo         INTEGER NOT NULL DEFAULT 1,
    precio_unitario_platillo  DOUBLE PRECISION NOT NULL DEFAULT 0,  -- se copia al pedir
    notas_detalle             VARCHAR(200)   -- NUEVA: "sin cebolla", "termino medio"
);

-- ---------------------------------------------------------------------
-- INDICES que ayudan a las consultas que mas se repiten
-- ---------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS ix_reservas_fecha        ON reservas (fecha_reserva);
CREATE INDEX IF NOT EXISTS ix_reservas_mesa_fecha   ON reservas (id_mesa, fecha_reserva);
CREATE INDEX IF NOT EXISTS ix_reservas_persona      ON reservas (id_persona);
CREATE INDEX IF NOT EXISTS ix_reservas_codigo       ON reservas (codigo_confirmacion);
CREATE INDEX IF NOT EXISTS ix_apartados_mesa_franja ON apartados_mesa (fecha, hora);
CREATE INDEX IF NOT EXISTS ix_pedidos_reserva       ON pedidos (id_reserva);

-- ---------------------------------------------------------------------
-- REGLAS DE NEGOCIO
--
-- El script original traia un trigger que revisaba la capacidad de la
-- mesa y el empalme de horarios. Esas dos reglas (y varias mas: aforo,
-- dias cerrados, apartado temporal, horarios validos) las aplica Spring
-- en ReservaServicio.java, para poder dar un mensaje entendible al
-- cliente en lugar de un error de base de datos.
--
-- El trigger se deja aqui como SEGUNDA LINEA DE DEFENSA: protege la
-- base aunque alguien escriba directo en PostgreSQL. Es el mismo del
-- script original, con la duracion leida de la tabla parametros.
-- ---------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_validar_reserva() RETURNS TRIGGER AS $$
DECLARE
    v_capacidad INTEGER;
    v_duracion  INTEGER;
BEGIN
    SELECT capacidad_mesa INTO v_capacidad FROM mesas WHERE id_mesa = NEW.id_mesa;
    IF NEW.cantidad_personas > v_capacidad THEN
        RAISE EXCEPTION 'En la mesa % caben % personas y la reserva es para %',
            NEW.id_mesa, v_capacidad, NEW.cantidad_personas;
    END IF;

    SELECT COALESCE(valor_parametro::INTEGER, 105) INTO v_duracion
    FROM parametros WHERE id_parametro = 'duracion_reserva_min';
    v_duracion := COALESCE(v_duracion, 105);

    IF NEW.id_estado_reserva IN ('CONFIRMADA', 'EN_MESA') AND EXISTS (
        SELECT 1 FROM reservas r
        WHERE r.id_mesa = NEW.id_mesa
          AND r.fecha_reserva = NEW.fecha_reserva
          AND r.id_reserva <> COALESCE(NEW.id_reserva, -1)
          AND r.id_estado_reserva IN ('CONFIRMADA', 'EN_MESA')
          AND NEW.hora_reserva < r.hora_reserva + (v_duracion || ' minutes')::INTERVAL
          AND r.hora_reserva < NEW.hora_reserva + (v_duracion || ' minutes')::INTERVAL
    ) THEN
        RAISE EXCEPTION 'La mesa % ya esta ocupada a esa hora', NEW.id_mesa;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_validar_reserva ON reservas;
CREATE TRIGGER tr_validar_reserva
    BEFORE INSERT OR UPDATE ON reservas
    FOR EACH ROW EXECUTE FUNCTION fn_validar_reserva();
