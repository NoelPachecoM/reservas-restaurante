import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ApiService } from './api.service';
import { API_URL } from './api-config';

/**
 * Agrega el token de sesión a cada petición que va a Spring:
 *   Authorization: Bearer <token>
 *
 * Si Spring responde que el token ya no sirve (venció o se cerró la sesión), se borra la sesión
 * del navegador. Un 401 por otra causa (por ejemplo, escribir mal la contraseña actual al
 * cambiar el nombre) NO cierra la sesión.
 */
export const tokenInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  if (!peticion.url.startsWith(API_URL)) return siguiente(peticion);

  const api = inject(ApiService);
  const token = api.token();
  if (!token) return siguiente(peticion);

  const conToken = peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  return siguiente(conToken).pipe(
    catchError((error: unknown) => {
      const mensaje =
        error instanceof HttpErrorResponse && typeof error.error?.error === 'string' ? error.error.error : '';
      const sesionVencida = mensaje.toLowerCase().startsWith('inicia sesion');
      if (error instanceof HttpErrorResponse && error.status === 401 && sesionVencida && api.token() === token) {
        api.olvidarSesion();
      }
      return throwError(() => error);
    }),
  );
};
