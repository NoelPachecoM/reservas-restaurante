# Sistema de reservas y pedidos para restaurante

Proyecto de demostración: una página donde el cliente **elige en el croquis la mesa
exacta donde se quiere sentar**, deja su pedido listo si quiere, y el personal trabaja
el turno desde la vista de piso y la pantalla de cocina.

- **Angular 22** (componentes standalone y signals) para la página.
- **Spring Boot 4.1 + PostgreSQL** para la API y las reglas de negocio.
- Parte de la base de datos que te pasaron (`base-de-datos/01-estructura-original.sql`),
  con las tablas y columnas que hacían falta para el croquis.

---

## 1. Qué necesitas instalado

| Programa   | Versión     | Para qué |
|------------|-------------|----------|
| Node.js    | 20 o más    | Angular |
| Java (JDK) | 17 o más    | Spring Boot |
| PostgreSQL | 14 o más    | La base de datos |

---

## 2. Arrancarlo en tu computadora

### 2.1 La base de datos

Crea una base vacía. **No hace falta correr ningún script**: al arrancar, Spring crea
las tablas solo y mete los datos de ejemplo.

```sql
CREATE DATABASE restaurante_reservas;
```

Luego copia `spring-reservas/src/main/resources/application-local.properties.ejemplo`
como `application-local.properties` (en la misma carpeta) y escribe ahí tu contraseña
de PostgreSQL. Ese archivo está en `.gitignore`: nunca se sube a GitHub.

### 2.2 Spring (la API)

```bash
cd spring-reservas
./gradlew bootRun          # en Windows: gradlew.bat bootRun
```

Queda escuchando en `http://localhost:8080`.

La primera vez crea las tablas y carga: 6 áreas, 24 mesas acomodadas en el croquis,
28 platillos, las cuentas de prueba y unas 75 reservas repartidas entre las tres
semanas pasadas y los diez días que vienen, para que la demostración no se vea vacía.

### 2.3 Angular (la página)

En otra terminal, desde la carpeta raíz del proyecto:

```bash
npm install
npm start
```

Abre `http://localhost:4200`.

### 2.4 Cuentas de prueba

| Correo | Contraseña | Entra como |
|--------|------------|------------|
| `admin@restaurante.local` | `Admin1234` | Administrador |
| `mesero@restaurante.local` | `Mesero1234` | Empleado |
| `cliente@restaurante.local` | `Cliente1234` | Cliente |

El código para que alguien se registre como empleado es **`EMP-DEMO26`**
(el administrador lo puede cambiar desde su panel).

---

## 3. Qué hace el sistema

### Para el cliente (sin cuenta o con cuenta)

- **Portada y carta.** La carta se filtra por etiquetas (vegetariano, picante, sin gluten)
  y cada platillo dice qué alérgenos contiene.
- **Reservar en tres pasos:**
  1. Cuántos van, qué día (calendario que pinta en verde, amarillo o rojo según qué tan
     lleno está) y a qué hora (cada horario dice cuántas mesas quedan).
  2. **El croquis.** El plano real del restaurante con sus áreas. Las mesas libres se ven
     en verde, las reservadas con línea punteada, y las que no alcanzan para el grupo se
     ven apagadas. Al tocar una mesa **queda apartada 10 minutos** con un reloj en pantalla,
     para que nadie más se la gane mientras termina.
  3. Confirmar: ocasión, notas para la cocina, y si quiere, **dejar el pedido listo**.
     Quien no tiene cuenta la crea ahí mismo sin perder la mesa apartada (la mesa sigue
     apartada mientras escribe el código que le llega al correo).
- **Verificación del correo al crear la cuenta.** Llega un código de 6 dígitos y la cuenta
  **no se crea** hasta que se escribe bien: así nadie se registra con un correo que no es
  suyo. Se puede reenviar (esperando un minuto) y vence en 20 minutos.
- **Código de confirmación de la reserva** (`R-XXXXXX`), que es otra cosa: es el que se
  muestra al llegar al restaurante. También llega por correo y se puede consultar desde la
  portada sin iniciar sesión.
- **Mis reservas:** cambiar, cancelar, ver la cuenta y agregar platillos.
- **Mi cuenta (derechos ARCO):** ver lo que el sistema guarda de él, corregir su nombre,
  teléfono y contraseña, **cambiar su correo** y **darse de baja**. Los dos últimos no se
  pueden deshacer, así que piden la contraseña **y** un código por correo (ver abajo).
- **Lista de espera** cuando no quedan mesas ese día.

### Para el personal (empleado y administrador)

- **Vista de piso:** el croquis en vivo con el nombre de quien ocupa cada mesa, la agenda
  del día, y los clientes que **ya pasaron de la tolerancia** marcados en amarillo.
  Desde ahí: marcar llegada, mover a otra mesa, levantar el pedido, cobrar (ver la cuenta),
  liberar la mesa o marcar que no se presentaron.
- **Sentar a alguien sin reserva:** tocar una mesa libre, poner el nombre y listo.
- **Buscar por código** cuando el cliente llega con su `R-XXXXXX`.
- **Cocina:** los pedidos pendientes en orden, con los minutos que llevan esperando
  (se ponen en rojo a los 20 minutos) y las notas de cada platillo.

### Para el administrador

- **Croquis y mesas:** acomodar el plano **arrastrando las mesas** y guardar; dar de alta,
  editar y quitar mesas (número, capacidad, forma, área, etiquetas).
- **Carta y áreas:** alta, baja y cambio de platillos, áreas y días cerrados.
- **Reservas:** todas, con filtro por fechas.
- **Números:** reservas y comensales del periodo, porcentaje de gente que no se presentó,
  venta pedida por la página, reservas por día y por hora, áreas más pedidas y platillos
  más vendidos.
- **Configuración:** horario, duración de la reserva, aforo, tolerancia, minutos de
  apartado, días de anticipación, y los textos de la portada. **Nada de esto está escrito
  en el código**: se lee de la tabla `parametros`, así que un cambio se ve de inmediato.
- **Cuentas:** cambiar el rol de alguien, activar o desactivar una cuenta y poner una
  contraseña temporal a quien no puede entrar (el sistema le pide cambiarla al entrar).

---

## 4. Derechos ARCO: los trámites que piden código

Dos acciones cambian o borran datos personales para siempre, así que ninguna se hace solo
con la sesión abierta: las dos piden la **contraseña** y además un **código de 6 dígitos**
que llega por correo. Mientras el trámite no se termine, en la cuenta no cambia nada.

**Dar de baja la cuenta (cancelación)**

1. La persona escribe su contraseña → sale un código a su correo.
2. Escribe el código (y la contraseña otra vez) → hasta ahí se borra la cuenta.

Se borran su nombre, su teléfono y su correo, y se cancelan sus reservas que no han pasado.
Las reservas viejas se quedan sin sus datos personales, porque son la contabilidad del
restaurante. Después puede volver a registrarse con el mismo correo.

**Cambiar el correo (rectificación)**

Aquí son **dos códigos**, porque hay dos cosas distintas que comprobar:

1. Escribe el correo nuevo y su contraseña → sale un código **al correo de ahora**.
   Eso comprueba que el cambio lo está pidiendo el dueño de la cuenta y no alguien que
   agarró la sesión abierta.
2. Confirma ese código → sale otro código **al correo nuevo**. Eso comprueba que el correo
   nuevo existe y es suyo.
3. Confirma el segundo → hasta ahí se mueve la cuenta al correo nuevo.

Si se corta en cualquier paso, la cuenta se queda igual. Al terminar, al correo viejo le
llega un aviso de que la cuenta se movió (con el nuevo a medias tapado), por si el cambio
no lo hizo el dueño. Los códigos vencen en 20 minutos, aguantan 5 intentos y se pueden
reenviar esperando un minuto.

---

## 5. Las reglas que revisa el servidor

Nunca se confía en lo que manda la página. Antes de guardar una reserva, Spring revisa:

1. Que la fecha y la hora no hayan pasado.
2. Que el restaurante abra ese día (no esté en días cerrados).
3. Que la hora sea una de las franjas válidas (de la apertura hasta el cierre menos la
   duración de la reserva).
4. Que en la mesa quepa el grupo.
5. Que la mesa no se empalme con otra reserva activa, comparando los rangos completos
   (una reserva de 20:00 choca con una de 20:30 si duran 105 minutos).
6. Que esa mesa no esté apartada por alguien más en ese momento.
7. Que el restaurante no pase su aforo en esa franja.
8. Que quien cambia o cancela sea el dueño de la reserva, y que falten al menos las horas
   de anticipación configuradas.

Además, cada ruta revisa quién eres por el **token de sesión**, no por lo que diga la
página: `/api/publico/**` es abierto, `/api/piso/**` pide empleado o administrador y
`/api/admin/**` pide administrador.

---

## 6. Qué le agregamos a la base de datos

El script original servía para guardar reservas, pero no alcanzaba para que el cliente
eligiera su mesa. Todo lo agregado está documentado en
`base-de-datos/03-estructura-del-sistema.sql`. En resumen:

| Qué | Por qué |
|-----|---------|
| `zonas` (nueva) | Las áreas del restaurante, con medidas en metros: es el fondo del croquis. |
| `mesas.posicion_x`, `posicion_y`, `id_zona`, `forma_mesa`, `etiquetas_mesa` | Sin la posición no se puede dibujar el plano. |
| `apartados_mesa` (nueva) | Guarda la mesa unos minutos mientras el cliente termina de reservar. |
| `personas.telefono_persona`, `correo_persona` | Sin esto no hay cómo confirmar ni avisar nada. |
| `reservas.codigo_confirmacion`, `ocasion_reserva`, `notas_reserva`, `hora_llegada`, `hora_salida` | Confirmar al llegar, avisar alergias y medir cuánto duran de verdad las mesas. |
| `platillos.etiquetas_platillo`, `alergenos_platillo` | Filtrar la carta y avisar alérgenos. |
| `pedido_detalle.id_detalle` y `notas_detalle` | **Cambio al diseño original:** con la llave compuesta (pedido + platillo) era imposible pedir dos veces el mismo platillo con notas distintas. |
| `solicitudes_registro` (nueva) | Guarda el registro a medias mientras se verifica el correo. La cuenta se crea hasta que el código es correcto. |
| `solicitudes_cuenta` (nueva) | Los trámites ARCO a medio hacer: la baja y el cambio de correo con sus dos códigos. |
| `fechas_bloqueadas`, `lista_espera`, `parametros` extra, `sesiones`, `codigos_recuperacion` | Días cerrados, lista de espera, configuración, sesiones con token y recuperar contraseña. |

También se cambió el cifrado de contraseñas: el script original guardaba **SHA-256**, que
para contraseñas se rompe rápido con tablas precalculadas. Aquí se usa **BCrypt**, que está
hecho para eso.

---

## 7. Qué NO trae (y por qué)

Es una demostración, así que estas partes se dejaron fuera a propósito:

- **Entrar con Google.** La tabla `origenes_cuenta` ya lo contempla, pero no se conectó
  el OAuth real.
- **Recordatorio automático** el día anterior. Los correos de confirmación, cambio y
  cancelación sí se mandan.
- **Pagos en línea.** El pago es en el restaurante; el sistema solo lleva la cuenta.
- **Juntar mesas** para un grupo grande (se resuelve con el salón privado).
- **Oposición al tratamiento por separado.** De los derechos ARCO están Acceso,
  Rectificación y Cancelación; la Oposición se resuelve cancelando las reservas o dándose
  de baja, no con una pantalla aparte de preferencias de avisos.

Si no hay correo configurado, los mensajes **se imprimen en la consola de Spring**, que
para la demostración es suficiente. Para que salgan de verdad, llena `spring.mail.*`
(Gmail con contraseña de aplicación) o `RESEND_API_KEY` en `application-local.properties`.

Como el registro ahora necesita que el código llegue, si estás probando **sin correo
configurado** puedes poner `registro.devolver-codigo=true` en `application-local.properties`:
con eso la API regresa el código en la respuesta y la pantalla lo llena sola. En un servidor
de verdad déjalo en `false`.

---

## 8. Publicarlo

Los pasos exactos para **paginapro.store**, con tus cuentas de GitHub, Vercel, Render,
Supabase, Namecheap y Resend, están en **[DESPLIEGUE.md](DESPLIEGUE.md)**.

En corto:

1. **La página** se compila con `npm run build` y se sube a Vercel.
   El `vercel.json` ya manda todas las rutas a `index.html`.
2. **Spring** se sube a Render con el `Dockerfile` que está en `spring-reservas/`.
3. **La base** es Supabase, en el esquema `reservas` (variable `DB_SCHEMA`), para no
   mezclarse con otros proyectos que vivan en `public`.
4. En `src/app/api/api-config.ts` están las **tres constantes** del dominio: cámbialas y
   toda la página apunta al servidor nuevo.
5. En Render pon las variables `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_SCHEMA`,
   `ADMIN_CORREO`, `ADMIN_PASSWORD`, `CORS_ORIGENES`, `RESEND_API_KEY` y
   `CORREO_REMITENTE`. Pon `DATOS_EJEMPLOS=false` para arrancar con el restaurante vacío.

También puedes servir todo desde Spring (una sola dirección):

```bash
npm run build:spring
cd spring-reservas && ./gradlew bootRun
```

y abrir `http://localhost:8080`.

---

## 9. Cómo está organizado

```
ReservasRestaurante/
├── base-de-datos/            Los scripts que te pasaron y la estructura final documentada
├── src/app/
│   ├── api/                  Llamadas a Spring, sesión, guardias de ruta
│   ├── componentes/
│   │   ├── croquis/          EL CROQUIS: se usa en reservar, piso y administración
│   │   └── encabezado/       La barra de arriba
│   └── paginas/              inicio, carta, reservar, mis-reservas, reserva-detalle,
│                             piso, cocina, administracion, mi-cuenta, login, recuperar
└── spring-reservas/src/main/java/com/example/reservas/
    ├── modelo/               Las tablas
    ├── repositorio/          Las consultas
    ├── servicio/             LAS REGLAS: ReservaServicio, PedidoServicio, CuentaServicio,
    │                         ArcoServicio (baja y cambio de correo con codigo)
    ├── controlador/          Las rutas de la API
    └── config/               Token de sesión, permisos, CORS y los datos de ejemplo
```

El croquis es un solo componente (`componentes/croquis/`) que se comporta distinto según
el modo: `elegir` para el cliente, `piso` para el mesero y `acomodar` para arrastrar las
mesas en administración.
