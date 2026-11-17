import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ApiService } from './api.service';

/** Si no hay sesión, manda al login y recuerda a qué página se quería entrar. */
export const guardiaSesion: CanActivateFn = (_ruta, estado) => {
  const api = inject(ApiService);
  return api.haIniciadoSesion()
    ? true
    : inject(Router).createUrlTree(['/entrar'], { queryParams: { volver: estado.url } });
};

/** Vista de piso y cocina: empleados y administrador. */
export const guardiaPersonal: CanActivateFn = (_ruta, estado) => {
  const api = inject(ApiService);
  const router = inject(Router);
  if (!api.haIniciadoSesion()) {
    return router.createUrlTree(['/entrar'], { queryParams: { volver: estado.url } });
  }
  return api.esPersonal() ? true : router.createUrlTree(['/']);
};

/** Panel de administración. */
export const guardiaAdmin: CanActivateFn = (_ruta, estado) => {
  const api = inject(ApiService);
  const router = inject(Router);
  if (!api.haIniciadoSesion()) {
    return router.createUrlTree(['/entrar'], { queryParams: { volver: estado.url } });
  }
  return api.esAdmin() ? true : router.createUrlTree(['/']);
};
