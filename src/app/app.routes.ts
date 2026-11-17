import { Routes } from '@angular/router';
import { Inicio } from './paginas/inicio/inicio';
import { Carta } from './paginas/carta/carta';
import { Reservar } from './paginas/reservar/reservar';
import { MisReservas } from './paginas/mis-reservas/mis-reservas';
import { ReservaDetalle } from './paginas/reserva-detalle/reserva-detalle';
import { Piso } from './paginas/piso/piso';
import { Cocina } from './paginas/cocina/cocina';
import { Administracion } from './paginas/administracion/administracion';
import { MiCuenta } from './paginas/mi-cuenta/mi-cuenta';
import { Login } from './paginas/login/login';
import { Recuperar } from './paginas/recuperar/recuperar';
import { guardiaAdmin, guardiaPersonal, guardiaSesion } from './api/guards';

export const routes: Routes = [
  { path: '', component: Inicio, pathMatch: 'full' },
  { path: 'carta', component: Carta },
  // Reservar se puede empezar sin cuenta; el último paso pide iniciar sesión.
  { path: 'reservar', component: Reservar },
  { path: 'reservar/:id', component: Reservar, canActivate: [guardiaSesion] },
  { path: 'mis-reservas', component: MisReservas, canActivate: [guardiaSesion] },
  { path: 'reserva/:id', component: ReservaDetalle, canActivate: [guardiaSesion] },
  { path: 'piso', component: Piso, canActivate: [guardiaPersonal] },
  { path: 'cocina', component: Cocina, canActivate: [guardiaPersonal] },
  { path: 'administracion', component: Administracion, canActivate: [guardiaAdmin] },
  { path: 'mi-cuenta', component: MiCuenta, canActivate: [guardiaSesion] },
  { path: 'entrar', component: Login },
  { path: 'recuperar', component: Recuperar },
  { path: '**', redirectTo: '' },
];
