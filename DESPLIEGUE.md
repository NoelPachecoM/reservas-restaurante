# Subir el sistema a paginapro.store

Guía hecha para tus cuentas tal como las tienes hoy: GitHub **NoelPachecoM**, Vercel
(equipo **Noel**, plan Hobby), Render (proyecto **My project**, donde ya vive
`streamlist-api`), Supabase (organización **NoelPacheco**, plan Free con los proyectos
`fan-points` y `streamlist`), Namecheap con **paginapro.store** y Resend.

**StreamList no se toca en ningún paso.** Todo lo de este proyecto se crea aparte.

Al final queda así:

| Dirección | Qué vive ahí | Dónde está |
|-----------|--------------|------------|
| `https://paginapro.store` y `www` | La página (Angular) | Vercel, proyecto nuevo |
| `https://api-reservas.paginapro.store` | La API (Spring) | Render, servicio nuevo |
| — | Las tablas | Supabase, esquema `reservas` dentro de tu proyecto `streamlist` |

---

## 0. Antes de empezar: dónde van las tablas

Tu plan Free de Supabase permite **2 proyectos activos** y ya tienes los dos
(`fan-points` y `streamlist`). Así que hay dos caminos:

**Camino A (el que recomiendo): un esquema aparte dentro de `streamlist`.**
Las tablas de StreamList viven en el esquema `public`. Este sistema va a crear las suyas
en un esquema llamado `reservas`, que es como una carpeta separada dentro de la misma base.
Son tablas distintas con nombres propios: **no se mezclan ni se pisan**. Esto ya está
contemplado en el código (`spring.jpa.properties.hibernate.default_schema`), solo hay que
poner la variable `DB_SCHEMA=reservas`.

**Camino B: pausar `fan-points` y crear un proyecto nuevo de Supabase.**
Queda más separado, pero `fan-points` deja de responder mientras esté pausado. Si eliges
este camino, el único cambio es que `DB_SCHEMA` se queda en `public` y el `DB_URL` apunta
al proyecto nuevo.

Abajo voy por el **camino A**.

---

## 1. Subir el proyecto a GitHub

En tu computadora, dentro de la carpeta del proyecto:

```bash
git init
git add .
git commit -m "Sistema de reservas de restaurante"
git branch -M main
git remote add origin https://github.com/NoelPachecoM/reservas-restaurante.git
git push -u origin main
```

Primero crea el repo vacío en GitHub (**New repository** → nombre `reservas-restaurante`
→ **sin** README ni .gitignore, porque el proyecto ya los trae).

El `.gitignore` ya deja fuera `node_modules`, `dist` y, lo importante,
`application-local.properties`: **tus contraseñas no se suben**.

---

## 2. La base de datos en Supabase

1. Entra a tu proyecto **streamlist** → botón **Connect** (arriba).
2. Elige la pestaña **Session pooler** (no la "Direct connection": esa es solo IPv6 y
   Render no la alcanza; tampoco el "Transaction pooler", que da problemas con Hibernate).
3. De esa cadena saca tres datos:
   - el **host**, algo como `aws-1-us-west-2.pooler.supabase.com`
   - el **usuario**, algo como `postgres.abcdefghijklmnop`
   - el **puerto**, `5432`
4. La contraseña es la de la base de datos del proyecto (si no la recuerdas:
   **Settings → Database → Reset database password**; ojo, si la cambias tienes que
   actualizarla también en el servicio de StreamList en Render).

No tienes que crear el esquema a mano: al arrancar, Spring crea `reservas` y sus tablas solo.

---

## 3. La API en Render

En tu proyecto **My project** de Render, **New → Web Service** (uno nuevo, no toques
`streamlist-api`):

| Campo | Valor |
|-------|-------|
| Repository | `NoelPachecoM/reservas-restaurante` |
| Name | `reservas-api` |
| Language / Runtime | **Docker** |
| Root Directory | `spring-reservas` |
| Region | **Oregon** (la misma que tu otro servicio) |
| Instance Type | Free |

El `Dockerfile` ya está dentro de `spring-reservas`, así que Render lo encuentra solo.

### Variables de entorno

En **Environment → Add environment variable**:

| Variable | Valor |
|----------|-------|
| `DB_URL` | `jdbc:postgresql://TU_HOST_DE_SUPABASE:5432/postgres` |
| `DB_USER` | `postgres.TU_REFERENCIA` |
| `DB_PASSWORD` | la contraseña de la base |
| `DB_SCHEMA` | `reservas` |
| `DB_POOL` | `3` |
| `ADMIN_CORREO` | tu correo, por ejemplo `elihuvelasco88@gmail.com` |
| `ADMIN_PASSWORD` | **una contraseña tuya, fuerte** (con esta entras como administrador) |
| `CORS_ORIGENES` | `https://paginapro.store,https://www.paginapro.store,https://*.vercel.app` |
| `RESEND_API_KEY` | tu llave de Resend (`re_...`) |
| `CORREO_REMITENTE` | `Mesa Nuestra <no-responder@paginapro.store>` |
| `DATOS_EJEMPLOS` | `true` para que la demostración tenga mesas, carta y reservas |

`PORT` no la pongas: Render la mete sola y Spring ya la lee.

> **Sobre `ADMIN_PASSWORD`:** ponle una tuya de verdad. Con `DATOS_EJEMPLOS=true` la página
> de entrar enseña las cuentas de prueba del mesero y del cliente (eso está bien, es una
> demostración), pero **ya no enseña la del administrador**: esa es tuya.
>
> Si algún día pones `DATOS_EJEMPLOS=false`, el restaurante arranca vacío (sin mesas ni
> carta) y la página deja de mostrar cualquier cuenta de prueba.

### El dominio de la API

En el servicio → **Settings → Custom Domains → Add Custom Domain**:
escribe `api-reservas.paginapro.store`. Render te va a dar un destino tipo
`reservas-api.onrender.com`.

Luego, en **Namecheap → paginapro.store → Advanced DNS → Add New Record**:

| Type | Host | Value | TTL |
|------|------|-------|-----|
| CNAME Record | `api-reservas` | `reservas-api.onrender.com.` | Automatic |

**No borres ni cambies los dos registros que ya tienes** (el `A @` y el `CNAME www`): esos
son los de Vercel y los sigue usando la página.

En unos minutos Render pone el candado (HTTPS) solo.

---

## 4. La página en Vercel

**Add New → Project** → importa `NoelPachecoM/reservas-restaurante`.

| Campo | Valor |
|-------|-------|
| Framework Preset | Angular |
| Root Directory | `./` (la raíz) |
| Build Command | `npm run build` (el que trae por defecto) |
| Output Directory | el que detecta solo (`dist/reservas-restaurante/browser`) |

No necesitas variables de entorno aquí: la dirección de la API ya está en el código
(`src/app/api/api-config.ts`).

Luego **Settings → Domains → Add**: agrega `paginapro.store` y `www.paginapro.store`.

Vercel te va a enseñar los valores de DNS que espera. El `A @ → 216.198.79.1` que ya
tienes es el correcto; el **CNAME de `www` sí revísalo**, porque Vercel a veces pide uno
propio del proyecto (tipo `a1b2c3d4.vercel-dns-017.com`) en lugar de
`cname.vercel-dns.com`. Si te pide uno distinto, edita ese registro en Namecheap y deja el
resto igual.

> Como la página ya apuntaba a Vercel, en cuanto agregues el dominio a **este** proyecto
> deja de servir lo que hubiera antes y empieza a servir el restaurante.

---

## 5. Los correos en Resend

Esto es importante: ahora **crear una cuenta, cambiar el correo y dar de baja la cuenta
necesitan que el código llegue**. Si el correo no sale, nadie puede registrarse.

1. Resend → **Domains → Add Domain** → `paginapro.store`.
2. Te da unos registros (MX, TXT de SPF y TXT de DKIM). Agrégalos en
   **Namecheap → Advanced DNS** tal cual, sin tocar los que ya están.
3. Cuando Resend marque el dominio como **Verified**, el valor de `CORREO_REMITENTE` que
   pusiste en Render ya sirve.

Mientras se verifica (puede tardar un rato), puedes dejar `CORREO_REMITENTE` apuntando al
dominio que **ya** tienes verificado en Resend, para no quedarte sin correos.

---

## 6. Revisar que todo quedó

1. Abre `https://api-reservas.paginapro.store/api/publico/restaurante`.
   Debe responder un JSON con el nombre del restaurante. La primera vez tarda ~1 minuto
   (ver la nota de abajo).
2. Abre `https://paginapro.store`. Debe salir la portada con las áreas y la carta.
3. Entra a **Reservar**: si el croquis dibuja las mesas, la página está hablando con la API
   y el CORS quedó bien.
4. Crea una cuenta con un correo tuyo: debe llegarte el código de 6 dígitos.
5. Entra como administrador con el `ADMIN_CORREO` y `ADMIN_PASSWORD` que pusiste, y
   cambia el nombre, la dirección y el teléfono del restaurante desde
   **Administración → Configuración**.

### Dos cosas que te van a pasar y son normales

- **La primera carga es lenta.** El plan Free de Render apaga el servicio cuando nadie lo
  usa por 15 minutos, y tarda entre 30 y 60 segundos en despertar. Si vas a presentarlo,
  **abre la página 2 minutos antes** para que ya esté despierto.
- **Supabase Free pausa los proyectos** que pasan 7 días sin ninguna consulta. Como
  compartes el proyecto con StreamList, mientras uses cualquiera de los dos se mantiene
  despierto.

---

## 7. Si después quieres cambiar de dominio

Todo está en **tres constantes** de `src/app/api/api-config.ts`:

```ts
const DOMINIO_PAGINA = 'paginapro.store';
const API_DOMINIO = 'https://api-reservas.paginapro.store';
const API_DESARROLLO = 'http://localhost:8080';
```

Cambias esas, haces `git push`, y Vercel vuelve a publicar solo. Del lado de Spring,
actualizas `CORS_ORIGENES` en Render y listo.

---

## 8. Resumen de qué toca cada servicio

| Servicio | Qué creas | Qué NO tocas |
|----------|-----------|--------------|
| GitHub | repo `reservas-restaurante` | `streamlist`, `the-weeknd-fanpage` |
| Vercel | proyecto nuevo + dominio `paginapro.store` | el proyecto `streamlist` |
| Render | servicio `reservas-api` en el mismo proyecto | el servicio `streamlist-api` |
| Supabase | el esquema `reservas` (lo crea Spring solo) | el esquema `public`, que es de StreamList |
| Namecheap | un CNAME `api-reservas` (+ los de Resend) | el `A @` y el `CNAME www` que ya existen |
| Resend | el dominio `paginapro.store` | la llave y el dominio que ya usas |
