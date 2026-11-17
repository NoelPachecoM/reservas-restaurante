-- DB restaurante_reservas_demo
-- Base de datos normalizada (3FN) de reservas de restaurante para PostgreSQL
-- Orden de ejecución: catálogos -> tablas principales -> detalle -> datos iniciales -> triggers


---------------------------------------------------------------------
-- CATÁLOGOS
---------------------------------------------------------------------

-- Script para la creación de la tabla 'roles' en PostgreSQL
CREATE TABLE roles (
    id_rol VARCHAR(20) PRIMARY KEY,
    nombre_rol VARCHAR(30) NOT NULL
);
COMMENT ON TABLE roles IS 'Catálogo de roles de cuenta (CLIENTE, EMPLEADO, ADMINISTRADOR).';
COMMENT ON COLUMN roles.id_rol IS 'Clave primaria, código que identifica el rol.';
COMMENT ON COLUMN roles.nombre_rol IS 'Nombre del rol que se muestra en pantalla.';


-- Script para la creación de la tabla 'origenes_cuenta' en PostgreSQL
CREATE TABLE origenes_cuenta (
    id_origen_cuenta VARCHAR(10) PRIMARY KEY,
    nombre_origen_cuenta VARCHAR(30) NOT NULL
);
COMMENT ON TABLE origenes_cuenta IS 'Catálogo del origen de las cuentas (LOCAL, GOOGLE).';
COMMENT ON COLUMN origenes_cuenta.id_origen_cuenta IS 'Clave primaria. LOCAL (registrada en el sistema) o GOOGLE (inicio con Google).';
COMMENT ON COLUMN origenes_cuenta.nombre_origen_cuenta IS 'Nombre del origen que se muestra en pantalla.';


-- Script para la creación de la tabla 'estados_reserva' en PostgreSQL
CREATE TABLE estados_reserva (
    id_estado_reserva VARCHAR(20) PRIMARY KEY,
    nombre_estado_reserva VARCHAR(30) NOT NULL
);
COMMENT ON TABLE estados_reserva IS 'Catálogo de estados de una reserva.';
COMMENT ON COLUMN estados_reserva.id_estado_reserva IS 'Clave primaria: CONFIRMADA, EN_MESA, FINALIZADA, CANCELADA, NO_SE_PRESENTO.';
COMMENT ON COLUMN estados_reserva.nombre_estado_reserva IS 'Nombre del estado que se muestra en pantalla.';


-- Script para la creación de la tabla 'estados_pedido' en PostgreSQL
CREATE TABLE estados_pedido (
    id_estado_pedido VARCHAR(20) PRIMARY KEY,
    nombre_estado_pedido VARCHAR(30) NOT NULL
);
COMMENT ON TABLE estados_pedido IS 'Catálogo de estados de un pedido.';
COMMENT ON COLUMN estados_pedido.id_estado_pedido IS 'Clave primaria: PENDIENTE, PREPARANDO, SERVIDO, CANCELADO.';
COMMENT ON COLUMN estados_pedido.nombre_estado_pedido IS 'Nombre del estado que se muestra en pantalla.';


-- Script para la creación de la tabla 'categorias_platillo' en PostgreSQL
CREATE TABLE categorias_platillo (
    id_categoria_platillo VARCHAR(20) PRIMARY KEY,
    nombre_categoria_platillo VARCHAR(30) NOT NULL
);
COMMENT ON TABLE categorias_platillo IS 'Catálogo de categorías del menú.';
COMMENT ON COLUMN categorias_platillo.id_categoria_platillo IS 'Clave primaria: ENTRADAS, PLATOS_FUERTES, POSTRES, BEBIDAS.';
COMMENT ON COLUMN categorias_platillo.nombre_categoria_platillo IS 'Nombre de la categoría que se muestra en pantalla.';


-- Script para la creación de la tabla 'parametros' en PostgreSQL
CREATE TABLE parametros (
    id_parametro VARCHAR(40) PRIMARY KEY,
    valor_parametro VARCHAR(100) NOT NULL,
    descripcion_parametro VARCHAR(255)
);
COMMENT ON TABLE parametros IS 'Parámetros de configuración del sistema (horario, duración de reserva, código de empleado).';
COMMENT ON COLUMN parametros.id_parametro IS 'Clave primaria, nombre que identifica el parámetro (por ejemplo hora_apertura).';
COMMENT ON COLUMN parametros.valor_parametro IS 'Valor del parámetro (texto).';
COMMENT ON COLUMN parametros.descripcion_parametro IS 'Para qué sirve el parámetro.';


---------------------------------------------------------------------
-- PERSONAS, USUARIOS Y MESAS
---------------------------------------------------------------------

-- Script para la creación de la tabla 'personas' en PostgreSQL
CREATE TABLE personas (
    id_persona SERIAL PRIMARY KEY,
    nombre_persona VARCHAR(100) NOT NULL
);
COMMENT ON TABLE personas IS 'Personas que reservan, tengan o no cuenta en el sistema.';
COMMENT ON COLUMN personas.id_persona IS 'Clave primaria, identificador de la persona (autonumérico).';
COMMENT ON COLUMN personas.nombre_persona IS 'Nombre completo. Se guarda una sola vez, para clientes con cuenta y para quienes reservan por teléfono o en persona.';
CREATE INDEX idx_personas_nombre ON personas(nombre_persona);


-- Script para la creación de la tabla 'usuarios' en PostgreSQL
CREATE TABLE usuarios (
    id_usuario SERIAL PRIMARY KEY,
    id_persona INT NOT NULL UNIQUE,
    nombre_usuario VARCHAR(120) NOT NULL UNIQUE,
    contrasena_hash VARCHAR(255) NOT NULL,
    id_rol VARCHAR(20) NOT NULL,
    id_origen_cuenta VARCHAR(10) NOT NULL DEFAULT 'LOCAL',
    usuario_activo BOOLEAN NOT NULL DEFAULT TRUE,
    debe_cambiar_contrasena BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_registro_usuario TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_usuarios_nombre_usuario CHECK (
        id_origen_cuenta = 'GOOGLE'
        OR (char_length(nombre_usuario) BETWEEN 3 AND 30 AND nombre_usuario NOT LIKE '%@%')
    ),
    FOREIGN KEY (id_persona) REFERENCES personas(id_persona) ON DELETE RESTRICT,
    FOREIGN KEY (id_rol) REFERENCES roles(id_rol) ON DELETE RESTRICT,
    FOREIGN KEY (id_origen_cuenta) REFERENCES origenes_cuenta(id_origen_cuenta) ON DELETE RESTRICT
);
COMMENT ON TABLE usuarios IS 'Cuentas de acceso al sistema.';
COMMENT ON COLUMN usuarios.id_usuario IS 'Clave primaria, identificador interno de la cuenta (autonumérico).';
COMMENT ON COLUMN usuarios.id_persona IS 'Persona dueña de la cuenta. Una persona tiene como máximo una cuenta (valor único).';
COMMENT ON COLUMN usuarios.nombre_usuario IS 'Nombre con el que inicia sesión (3 a 30 caracteres, sin @) o correo completo si la cuenta es de Google. Único.';
COMMENT ON COLUMN usuarios.contrasena_hash IS 'Hash de la contraseña, nunca el texto plano. En producción usar bcrypt o argon2.';
COMMENT ON COLUMN usuarios.id_rol IS 'Tipo de cuenta. Referencia a roles.';
COMMENT ON COLUMN usuarios.id_origen_cuenta IS 'Cómo se creó la cuenta. Referencia a origenes_cuenta. Por defecto LOCAL.';
COMMENT ON COLUMN usuarios.usuario_activo IS 'FALSE = cuenta desactivada, no puede iniciar sesión. Por defecto TRUE.';
COMMENT ON COLUMN usuarios.debe_cambiar_contrasena IS 'TRUE en el administrador inicial hasta que cambie su contraseña. Por defecto FALSE.';
COMMENT ON COLUMN usuarios.fecha_registro_usuario IS 'Fecha y hora en que se creó la cuenta.';
CREATE INDEX idx_usuarios_rol ON usuarios(id_rol);
CREATE INDEX idx_usuarios_origen_cuenta ON usuarios(id_origen_cuenta);


-- Script para la creación de la tabla 'mesas' en PostgreSQL
CREATE TABLE mesas (
    id_mesa INT PRIMARY KEY,
    capacidad_mesa INT NOT NULL CHECK (capacidad_mesa > 0),
    mesa_activa BOOLEAN NOT NULL DEFAULT TRUE
);
COMMENT ON TABLE mesas IS 'Mesas del restaurante.';
COMMENT ON COLUMN mesas.id_mesa IS 'Clave primaria; coincide con el número de mesa que ve el cliente.';
COMMENT ON COLUMN mesas.capacidad_mesa IS 'Máximo de personas que caben en la mesa.';
COMMENT ON COLUMN mesas.mesa_activa IS 'FALSE = fuera de servicio; no aparece para nuevas reservas. Por defecto TRUE.';


---------------------------------------------------------------------
-- RESERVAS
---------------------------------------------------------------------

-- Script para la creación de la tabla 'reservas' en PostgreSQL
CREATE TABLE reservas (
    id_reserva SERIAL PRIMARY KEY,
    id_persona INT NOT NULL,
    id_mesa INT NOT NULL,
    fecha_reserva DATE NOT NULL,
    hora_reserva TIME NOT NULL,
    cantidad_personas INT NOT NULL CHECK (cantidad_personas > 0),
    id_estado_reserva VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA',
    id_usuario_registro INT,
    fecha_registro_reserva TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_persona) REFERENCES personas(id_persona) ON DELETE RESTRICT,
    FOREIGN KEY (id_mesa) REFERENCES mesas(id_mesa) ON DELETE RESTRICT,
    FOREIGN KEY (id_estado_reserva) REFERENCES estados_reserva(id_estado_reserva) ON DELETE RESTRICT,
    FOREIGN KEY (id_usuario_registro) REFERENCES usuarios(id_usuario) ON DELETE RESTRICT
);
COMMENT ON TABLE reservas IS 'Reservas de mesa del restaurante.';
COMMENT ON COLUMN reservas.id_reserva IS 'Clave primaria, identificador de la reserva (autonumérico).';
COMMENT ON COLUMN reservas.id_persona IS 'Persona que reserva, tenga o no cuenta en el sistema.';
COMMENT ON COLUMN reservas.id_mesa IS 'Mesa reservada.';
COMMENT ON COLUMN reservas.fecha_reserva IS 'Día para el que se reserva.';
COMMENT ON COLUMN reservas.hora_reserva IS 'Hora de inicio. La mesa queda ocupada el tiempo indicado en parametros (duracion_reserva_min).';
COMMENT ON COLUMN reservas.cantidad_personas IS 'Número de comensales. Debe ser mayor a 0 y no mayor que mesas.capacidad_mesa (validado por trigger).';
COMMENT ON COLUMN reservas.id_estado_reserva IS 'Estado actual de la reserva. Por defecto CONFIRMADA.';
COMMENT ON COLUMN reservas.id_usuario_registro IS 'Empleado que la registró. NULL si la hizo el propio cliente.';
COMMENT ON COLUMN reservas.fecha_registro_reserva IS 'Fecha y hora en que se registró la reserva.';
CREATE INDEX idx_reservas_persona ON reservas(id_persona);
CREATE INDEX idx_reservas_mesa_fecha_hora ON reservas(id_mesa, fecha_reserva, hora_reserva);
CREATE INDEX idx_reservas_estado ON reservas(id_estado_reserva);
CREATE INDEX idx_reservas_usuario_registro ON reservas(id_usuario_registro);


---------------------------------------------------------------------
-- PEDIDOS
---------------------------------------------------------------------

-- Script para la creación de la tabla 'pedidos' en PostgreSQL
CREATE TABLE pedidos (
    id_pedido SERIAL PRIMARY KEY,
    id_reserva INT NOT NULL,
    id_estado_pedido VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_registro_pedido TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_reserva) REFERENCES reservas(id_reserva) ON DELETE RESTRICT,
    FOREIGN KEY (id_estado_pedido) REFERENCES estados_pedido(id_estado_pedido) ON DELETE RESTRICT
);
COMMENT ON TABLE pedidos IS 'Pedidos de comida asociados a una reserva.';
COMMENT ON COLUMN pedidos.id_pedido IS 'Clave primaria, identificador del pedido (autonumérico).';
COMMENT ON COLUMN pedidos.id_reserva IS 'Reserva a la que pertenece. De ahí salen la persona, la mesa y la fecha.';
COMMENT ON COLUMN pedidos.id_estado_pedido IS 'Estado actual del pedido. Por defecto PENDIENTE.';
COMMENT ON COLUMN pedidos.fecha_registro_pedido IS 'Fecha y hora en que se hizo el pedido.';
CREATE INDEX idx_pedidos_reserva ON pedidos(id_reserva);
CREATE INDEX idx_pedidos_estado ON pedidos(id_estado_pedido);


-- Script para la creación de la tabla 'platillos' en PostgreSQL
CREATE TABLE platillos (
    id_platillo SERIAL PRIMARY KEY,
    nombre_platillo VARCHAR(100) NOT NULL,
    descripcion_platillo VARCHAR(255),
    id_categoria_platillo VARCHAR(20) NOT NULL,
    precio_platillo DECIMAL(10,2) NOT NULL CHECK (precio_platillo >= 0),
    imagen_platillo VARCHAR(255),
    platillo_activo BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (id_categoria_platillo) REFERENCES categorias_platillo(id_categoria_platillo) ON DELETE RESTRICT
);
COMMENT ON TABLE platillos IS 'Menú de platillos del restaurante.';
COMMENT ON COLUMN platillos.id_platillo IS 'Clave primaria, identificador del platillo (autonumérico).';
COMMENT ON COLUMN platillos.nombre_platillo IS 'Nombre del platillo.';
COMMENT ON COLUMN platillos.descripcion_platillo IS 'Descripción corta del platillo.';
COMMENT ON COLUMN platillos.id_categoria_platillo IS 'Categoría del platillo. Referencia a categorias_platillo.';
COMMENT ON COLUMN platillos.precio_platillo IS 'Precio actual en MXN.';
COMMENT ON COLUMN platillos.imagen_platillo IS 'Ruta de la foto (por ejemplo img/platillo1.jpg).';
COMMENT ON COLUMN platillos.platillo_activo IS 'FALSE = oculto/agotado, el cliente no lo ve. Por defecto TRUE.';
CREATE INDEX idx_platillos_categoria ON platillos(id_categoria_platillo);
CREATE INDEX idx_platillos_nombre ON platillos(nombre_platillo);


-- Script para la creación de la tabla 'pedido_detalle' en PostgreSQL
CREATE TABLE pedido_detalle (
    id_pedido INT NOT NULL,
    id_platillo INT NOT NULL,
    cantidad_platillo INT NOT NULL CHECK (cantidad_platillo > 0),
    precio_unitario_platillo DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id_pedido, id_platillo),
    FOREIGN KEY (id_pedido) REFERENCES pedidos(id_pedido) ON DELETE CASCADE,
    FOREIGN KEY (id_platillo) REFERENCES platillos(id_platillo) ON DELETE RESTRICT
);
COMMENT ON TABLE pedido_detalle IS 'Platillos incluidos en cada pedido (una fila por platillo).';
COMMENT ON COLUMN pedido_detalle.id_pedido IS 'Parte de la clave primaria. Pedido al que pertenece la línea.';
COMMENT ON COLUMN pedido_detalle.id_platillo IS 'Parte de la clave primaria. Platillo pedido.';
COMMENT ON COLUMN pedido_detalle.cantidad_platillo IS 'Unidades pedidas de ese platillo.';
COMMENT ON COLUMN pedido_detalle.precio_unitario_platillo IS 'Precio de una unidad al momento de pedir. Se guarda porque platillos.precio_platillo puede cambiar y el historial no debe alterarse.';
CREATE INDEX idx_pedido_detalle_platillo ON pedido_detalle(id_platillo);


---------------------------------------------------------------------
-- DATOS INICIALES DE LOS CATÁLOGOS
-- (deben cargarse antes de usar el sistema)
---------------------------------------------------------------------

INSERT INTO roles (id_rol, nombre_rol) VALUES
    ('CLIENTE', 'Cliente'),
    ('EMPLEADO', 'Empleado'),
    ('ADMINISTRADOR', 'Administrador');

INSERT INTO origenes_cuenta (id_origen_cuenta, nombre_origen_cuenta) VALUES
    ('LOCAL', 'Cuenta del sistema'),
    ('GOOGLE', 'Cuenta de Google');

INSERT INTO estados_reserva (id_estado_reserva, nombre_estado_reserva) VALUES
    ('CONFIRMADA', 'Confirmada'),
    ('EN_MESA', 'En mesa'),
    ('FINALIZADA', 'Finalizada'),
    ('CANCELADA', 'Cancelada'),
    ('NO_SE_PRESENTO', 'No se presentó');

INSERT INTO estados_pedido (id_estado_pedido, nombre_estado_pedido) VALUES
    ('PENDIENTE', 'Pendiente'),
    ('PREPARANDO', 'Preparando'),
    ('SERVIDO', 'Servido'),
    ('CANCELADO', 'Cancelado');

INSERT INTO categorias_platillo (id_categoria_platillo, nombre_categoria_platillo) VALUES
    ('ENTRADAS', 'Entradas'),
    ('PLATOS_FUERTES', 'Platos fuertes'),
    ('POSTRES', 'Postres'),
    ('BEBIDAS', 'Bebidas');

INSERT INTO parametros (id_parametro, valor_parametro, descripcion_parametro) VALUES
    ('hora_apertura', '12:00', 'Primera hora a la que se puede reservar (constante HORA_APERTURA de la app).'),
    ('hora_cierre', '22:00', 'Última hora a la que se puede reservar (constante HORA_CIERRE de la app).'),
    ('duracion_reserva_min', '120', 'Minutos que una reserva ocupa la mesa (constante DURACION_MINUTOS de la app).'),
    ('codigo_empleado', 'EMP-XXXXXX', 'Código que debe escribir quien se registra como empleado. Lo genera y renueva el administrador; este valor es solo de ejemplo.');


---------------------------------------------------------------------
-- REGLAS DE NEGOCIO (triggers)
---------------------------------------------------------------------

-- Regla: capacidad de la mesa y choque de horario entre reservas activas
CREATE OR REPLACE FUNCTION fn_validar_reserva()
RETURNS TRIGGER AS $$
DECLARE
    v_capacidad INT;
    v_duracion INT;
BEGIN
    -- Capacidad: cantidad_personas no puede superar mesas.capacidad_mesa
    SELECT capacidad_mesa INTO v_capacidad
    FROM mesas
    WHERE id_mesa = NEW.id_mesa;

    IF NEW.cantidad_personas > v_capacidad THEN
        RAISE EXCEPTION 'La mesa % tiene capacidad para % personas y se solicitaron %.',
            NEW.id_mesa, v_capacidad, NEW.cantidad_personas;
    END IF;

    -- Choque de horario: solo aplica a reservas activas (CONFIRMADA o EN_MESA)
    IF NEW.id_estado_reserva IN ('CONFIRMADA', 'EN_MESA') THEN
        SELECT valor_parametro::INT INTO v_duracion
        FROM parametros
        WHERE id_parametro = 'duracion_reserva_min';

        IF EXISTS (
            SELECT 1
            FROM reservas r
            WHERE r.id_mesa = NEW.id_mesa
              AND r.id_estado_reserva IN ('CONFIRMADA', 'EN_MESA')
              AND r.id_reserva IS DISTINCT FROM NEW.id_reserva
              AND (NEW.fecha_reserva + NEW.hora_reserva) < (r.fecha_reserva + r.hora_reserva) + make_interval(mins => v_duracion)
              AND (r.fecha_reserva + r.hora_reserva) < (NEW.fecha_reserva + NEW.hora_reserva) + make_interval(mins => v_duracion)
        ) THEN
            RAISE EXCEPTION 'La mesa % ya tiene una reserva activa que se superpone con ese horario.', NEW.id_mesa;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validar_reserva
BEFORE INSERT OR UPDATE ON reservas
FOR EACH ROW
EXECUTE FUNCTION fn_validar_reserva();


-- Regla: un pedido solo puede crearse si su reserva está CONFIRMADA o EN_MESA
CREATE OR REPLACE FUNCTION fn_validar_pedido_reserva_activa()
RETURNS TRIGGER AS $$
DECLARE
    v_estado VARCHAR(20);
BEGIN
    SELECT id_estado_reserva INTO v_estado
    FROM reservas
    WHERE id_reserva = NEW.id_reserva;

    IF v_estado NOT IN ('CONFIRMADA', 'EN_MESA') THEN
        RAISE EXCEPTION 'Solo se pueden crear pedidos para reservas CONFIRMADA o EN_MESA (estado actual: %).', v_estado;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validar_pedido_reserva_activa
BEFORE INSERT ON pedidos
FOR EACH ROW
EXECUTE FUNCTION fn_validar_pedido_reserva_activa();


---------------------------------------------------------------------
-- CONSULTA DE REFERENCIA: total de un pedido (dato derivado, no se almacena)
---------------------------------------------------------------------
-- SELECT id_pedido, SUM(cantidad_platillo * precio_unitario_platillo) AS total_pedido
-- FROM pedido_detalle
-- GROUP BY id_pedido;
