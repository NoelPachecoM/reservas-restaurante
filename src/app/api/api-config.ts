/**
 * Dirección de la API de Spring, según dónde se abra la página:
 *
 *   - DOMINIO_PAGINA, su www o cualquier *.vercel.app  -> API_DOMINIO
 *   - localhost:4200 (npm start)                 -> API_DESARROLLO (Spring en tu computadora)
 *   - página servida por el mismo Spring         -> la misma dirección
 *
 * PARA CAMBIAR DE DOMINIO: edita solo estas tres constantes.
 */
const DOMINIO_PAGINA = 'paginapro.store';
const API_DOMINIO = 'https://reservas-api-ew53.onrender.com';
const API_DESARROLLO = 'http://localhost:8080';

function calcularUrl(): string {
  if (typeof window === 'undefined') return API_DOMINIO;
  const { protocol, host, hostname, port } = window.location;
  // El dominio, el www y las vistas previas de Vercel apuntan al Spring de producción
  if (hostname === DOMINIO_PAGINA
      || hostname === `www.${DOMINIO_PAGINA}`
      || hostname.endsWith('.vercel.app')) {
    return API_DOMINIO;
  }
  if (port === '4200') return API_DESARROLLO;
  return `${protocol}//${host}`;
}

export const API_URL = calcularUrl();
