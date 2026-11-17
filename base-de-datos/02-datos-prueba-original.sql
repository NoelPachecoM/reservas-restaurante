-- DB restaurante_reservas_demo : datos de prueba (2000 registros por tabla)
-- Ejecutar DESPUÉS de restaurante_reservas_demo.sql (estructura + catálogos).
-- Tablas con 2000 registros: personas, usuarios, mesas, reservas, pedidos, platillos, pedido_detalle
-- Los catálogos (roles, origenes_cuenta, estados_*, categorias_platillo, parametros) conservan sus valores reales.
-- Los datos son ficticios y se generan con generate_series (deterministas, sin aleatoriedad).


---------------------------------------------------------------------
-- 1. PERSONAS (2000)
---------------------------------------------------------------------
INSERT INTO personas (id_persona, nombre_persona)
SELECT
    g,
    (ARRAY['Juan','María','José','Ana','Luis','Carmen','Carlos','Laura','Miguel','Sofía',
           'Jorge','Elena','Pedro','Lucía','Diego','Paula','Andrés','Valeria','Fernando','Daniela',
           'Ricardo','Fernanda','Alejandro','Gabriela','Manuel','Mariana','Roberto','Patricia','Javier','Andrea',
           'Héctor','Claudia','Raúl','Isabel','Sergio','Rosa','Óscar','Teresa','Eduardo','Verónica'])[1 + (g % 40)]
    || ' ' ||
    (ARRAY['García','Hernández','López','Martínez','González','Pérez','Rodríguez','Sánchez','Ramírez','Cruz',
           'Flores','Gómez','Morales','Vázquez','Jiménez','Reyes','Díaz','Torres','Gutiérrez','Ruiz',
           'Mendoza','Aguilar','Ortiz','Castillo','Romero','Álvarez','Chávez','Rivera','Juárez','Ramos',
           'Medina','Herrera','Vargas','Castro','Navarro','Domínguez','Guerrero','Soto','Rojas','Delgado'])[1 + ((g / 3) % 40)]
    || ' ' ||
    (ARRAY['Silva','Luna','Campos','Salazar','Cortés','Fuentes','Ibarra','Montes','Peña','Ríos'])[1 + ((g / 7) % 10)]
FROM generate_series(1, 2000) AS g;


---------------------------------------------------------------------
-- 2. USUARIOS (2000, una cuenta por persona)
--    id 1 = ADMINISTRADOR | id terminado en 3 = EMPLEADO | resto = CLIENTE
--    Cada 5.º usuario es cuenta de Google (usuario = correo)
--    Contraseña de todos: 'Pass' + id, guardada como hash SHA-256 (solo demo)
---------------------------------------------------------------------
INSERT INTO usuarios (id_usuario, id_persona, nombre_usuario, contrasena_hash, id_rol,
                      id_origen_cuenta, usuario_activo, debe_cambiar_contrasena, fecha_registro_usuario)
SELECT
    g,
    g,
    CASE WHEN g % 5 = 0 THEN 'usuario' || g || '@gmail.com' ELSE 'usuario' || g END,
    encode(sha256(('Pass' || g)::bytea), 'hex'),
    CASE WHEN g = 1 THEN 'ADMINISTRADOR'
         WHEN g % 10 = 3 THEN 'EMPLEADO'
         ELSE 'CLIENTE' END,
    CASE WHEN g % 5 = 0 THEN 'GOOGLE' ELSE 'LOCAL' END,
    (g % 40 <> 0),                 -- 1 de cada 40 cuentas desactivada
    (g = 1),                       -- el administrador inicial debe cambiar su contraseña
    CURRENT_TIMESTAMP - make_interval(days => 365 - (g % 365), mins => g % 1440)
FROM generate_series(1, 2000) AS g;


---------------------------------------------------------------------
-- 3. MESAS (2000)  capacidad entre 2 y 10 personas
---------------------------------------------------------------------
INSERT INTO mesas (id_mesa, capacidad_mesa, mesa_activa)
SELECT
    g,
    2 + (g % 9),
    (g % 100 <> 0)                 -- 1 de cada 100 mesas fuera de servicio
FROM generate_series(1, 2000) AS g;


---------------------------------------------------------------------
-- 4. PLATILLOS (2000)  4 categorías, 500 por categoría
---------------------------------------------------------------------
INSERT INTO platillos (id_platillo, nombre_platillo, descripcion_platillo, id_categoria_platillo,
                       precio_platillo, imagen_platillo, platillo_activo)
SELECT
    g,
    CASE g % 4
        WHEN 0 THEN (ARRAY['Guacamole','Queso fundido','Sopa de tortilla','Ceviche','Tostadas de tinga','Esquites','Empanadas','Ensalada de nopales'])[1 + ((g / 4) % 8)]
        WHEN 1 THEN (ARRAY['Tacos al pastor','Enchiladas verdes','Mole poblano','Cochinita pibil','Arrachera','Pozole rojo','Chiles en nogada','Filete de res'])[1 + ((g / 4) % 8)]
        WHEN 2 THEN (ARRAY['Flan napolitano','Pastel de chocolate','Churros','Arroz con leche','Helado de vainilla','Tres leches','Cheesecake','Pay de limón'])[1 + ((g / 4) % 8)]
        ELSE        (ARRAY['Agua de jamaica','Horchata','Limonada','Café americano','Refresco','Cerveza artesanal','Margarita','Té helado'])[1 + ((g / 4) % 8)]
    END || ' #' || g,
    'Platillo de la casa preparado con ingredientes frescos.',
    (ARRAY['ENTRADAS','PLATOS_FUERTES','POSTRES','BEBIDAS'])[1 + (g % 4)],
    ROUND((30 + ((g * 37) % 470))::numeric, 2),       -- precios entre 30.00 y 499.00 MXN
    'img/platillo' || g || '.jpg',
    (g % 25 <> 0)                  -- 1 de cada 25 platillos oculto/agotado
FROM generate_series(1, 2000) AS g;


---------------------------------------------------------------------
-- 5. RESERVAS (2000)
--    Una reserva por mesa: así nunca hay choque de horario y el trigger
--    trg_validar_reserva (capacidad + superposición) se cumple.
--    Se insertan como CONFIRMADA y al final se diversifican los estados
--    (los pedidos solo pueden crearse sobre reservas activas).
---------------------------------------------------------------------
INSERT INTO reservas (id_reserva, id_persona, id_mesa, fecha_reserva, hora_reserva, cantidad_personas,
                      id_estado_reserva, id_usuario_registro, fecha_registro_reserva)
SELECT
    g,
    1 + ((g * 13) % 2000),                                             -- persona que reserva
    g,                                                                 -- mesa
    CURRENT_DATE + ((g % 120) - 60),                                   -- de 60 días atrás a 59 adelante
    make_time(12 + (g % 10), (g % 2) * 30, 0),                         -- de 12:00 a 21:30
    1 + (g % (2 + (g % 9))),                                           -- 1..capacidad de la mesa
    'CONFIRMADA',
    CASE WHEN g % 3 = 0 THEN ((g % 200) * 10) + 3 ELSE NULL END,      -- empleado que la registró (ids terminados en 3)
    (CURRENT_DATE + ((g % 120) - 60))::timestamp - make_interval(days => 1 + (g % 30), hours => g % 24)
FROM generate_series(1, 2000) AS g;


---------------------------------------------------------------------
-- 6. PEDIDOS (2000, uno por reserva)
---------------------------------------------------------------------
INSERT INTO pedidos (id_pedido, id_reserva, id_estado_pedido, fecha_registro_pedido)
SELECT
    r.id_reserva,
    r.id_reserva,
    (ARRAY['PENDIENTE','PREPARANDO','SERVIDO','CANCELADO'])[1 + (r.id_reserva % 4)],
    (r.fecha_reserva + r.hora_reserva) + make_interval(mins => 10 + (r.id_reserva % 40))
FROM reservas r;


---------------------------------------------------------------------
-- 7. PEDIDO_DETALLE (2000, una línea por pedido)
--    El precio unitario se copia del platillo al momento de pedir.
---------------------------------------------------------------------
INSERT INTO pedido_detalle (id_pedido, id_platillo, cantidad_platillo, precio_unitario_platillo)
SELECT
    p.id_pedido,
    pl.id_platillo,
    1 + (p.id_pedido % 5),
    pl.precio_platillo
FROM pedidos p
JOIN platillos pl ON pl.id_platillo = 1 + ((p.id_pedido * 7) % 2000);


---------------------------------------------------------------------
-- 8. Diversificar el estado de las reservas
---------------------------------------------------------------------
UPDATE reservas
SET id_estado_reserva = CASE id_reserva % 10
        WHEN 0 THEN 'FINALIZADA'
        WHEN 1 THEN 'FINALIZADA'
        WHEN 2 THEN 'FINALIZADA'
        WHEN 3 THEN 'CANCELADA'
        WHEN 4 THEN 'NO_SE_PRESENTO'
        WHEN 5 THEN 'EN_MESA'
        ELSE 'CONFIRMADA'
    END;


---------------------------------------------------------------------
-- 9. Ajustar las secuencias (SERIAL) después de insertar ids explícitos
---------------------------------------------------------------------
SELECT setval(pg_get_serial_sequence('personas',  'id_persona'),  (SELECT MAX(id_persona)  FROM personas));
SELECT setval(pg_get_serial_sequence('usuarios',  'id_usuario'),  (SELECT MAX(id_usuario)  FROM usuarios));
SELECT setval(pg_get_serial_sequence('reservas',  'id_reserva'),  (SELECT MAX(id_reserva)  FROM reservas));
SELECT setval(pg_get_serial_sequence('pedidos',   'id_pedido'),   (SELECT MAX(id_pedido)   FROM pedidos));
SELECT setval(pg_get_serial_sequence('platillos', 'id_platillo'), (SELECT MAX(id_platillo) FROM platillos));


---------------------------------------------------------------------
-- 10. Verificación: registros por tabla
---------------------------------------------------------------------
SELECT 'personas' AS tabla, COUNT(*) AS registros FROM personas
UNION ALL SELECT 'usuarios',       COUNT(*) FROM usuarios
UNION ALL SELECT 'mesas',          COUNT(*) FROM mesas
UNION ALL SELECT 'platillos',      COUNT(*) FROM platillos
UNION ALL SELECT 'reservas',       COUNT(*) FROM reservas
UNION ALL SELECT 'pedidos',        COUNT(*) FROM pedidos
UNION ALL SELECT 'pedido_detalle', COUNT(*) FROM pedido_detalle;
