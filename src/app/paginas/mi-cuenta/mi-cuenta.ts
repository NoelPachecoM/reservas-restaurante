import { Component, OnDestroy, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService, mensajeDeError } from '../../api/api.service';
import { Telefono } from '../../componentes/telefono.directive';

/**
 * Mi cuenta: los derechos ARCO sobre los datos personales.
 *
 *   Acceso        - ver lo que el sistema guarda de mí.
 *   Rectificación - corregir mi nombre, mi teléfono y mi correo.
 *   Cancelación   - pedir que se borren mis datos personales.
 *
 * Los dos trámites que no se pueden deshacer piden la contraseña Y un código por correo:
 *
 *   Borrar la cuenta → un código al correo de la cuenta.
 *   Cambiar el correo → un código al correo de HOY y después otro al correo NUEVO.
 *     Hasta que los dos se confirman, el correo no se mueve.
 */
@Component({
  selector: 'app-mi-cuenta',
  imports: [RouterLink, Telefono],
  templateUrl: './mi-cuenta.html',
  styleUrl: './mi-cuenta.css',
})
export class MiCuenta implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);

  readonly usuario = this.api.usuario;

  // ---------------- Mis datos ----------------
  readonly nombre = signal('');
  readonly telefono = signal('');
  readonly contrasenaNueva = signal('');
  readonly contrasenaActual = signal('');
  readonly error = signal('');
  readonly aviso = signal('');
  readonly guardando = signal(false);
  /** Cuando el administrador puso una contraseña temporal, se entra directo al cambio. */
  readonly obligado = signal(false);

  // ---------------- Cambiar el correo ----------------
  /** cerrado · datos (pide contraseña y correo nuevo) · actual (código 1) · nuevo (código 2) */
  readonly pasoCorreo = signal<'cerrado' | 'datos' | 'actual' | 'nuevo'>('cerrado');
  readonly correoNuevo = signal('');
  readonly contrasenaCorreo = signal('');
  readonly codigoCorreo = signal('');
  readonly solicitudCorreo = signal<number | null>(null);
  readonly avisoCorreo = signal('');
  readonly errorCorreo = signal('');

  // ---------------- Dar de baja ----------------
  /** cerrado · contrasena (pide la contraseña) · codigo (pide el código del correo) */
  readonly pasoBaja = signal<'cerrado' | 'contrasena' | 'codigo'>('cerrado');
  readonly contrasenaBaja = signal('');
  readonly codigoBaja = signal('');
  readonly solicitudBaja = signal<number | null>(null);
  readonly avisoBaja = signal('');
  readonly errorBaja = signal('');
  /** Al terminar se queda un mensaje en pantalla unos segundos y luego va a la portada. */
  readonly bajaLista = signal('');

  // ---------------- Reloj del reenvío ----------------
  readonly segundosParaReenviar = signal(0);
  private reloj: ReturnType<typeof setInterval> | null = null;

  constructor() {
    const u = this.usuario();
    if (u) {
      this.nombre.set(u.nombre);
      this.telefono.set(u.telefono ?? '');
      this.obligado.set(u.debeCambiarContrasena || this.ruta.snapshot.queryParamMap.has('cambiar'));
    }
  }

  ngOnDestroy(): void {
    this.detenerReloj();
  }

  // =====================================================================
  // Rectificación: nombre, teléfono y contraseña
  // =====================================================================

  async guardar(): Promise<void> {
    this.error.set('');
    this.aviso.set('');
    this.guardando.set(true);
    try {
      await this.api.cambiarMiCuenta({
        contrasenaActual: this.contrasenaActual(),
        nombre: this.nombre().trim(),
        telefono: this.telefono().trim(),
        contrasenaNueva: this.contrasenaNueva() || undefined,
      });
      this.contrasenaActual.set('');
      this.contrasenaNueva.set('');
      this.obligado.set(false);
      this.aviso.set('Tus datos quedaron guardados.');
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.guardando.set(false);
    }
  }

  // =====================================================================
  // Cambiar el correo (dos códigos)
  // =====================================================================

  abrirCambioDeCorreo(): void {
    this.pasoCorreo.set('datos');
    this.correoNuevo.set('');
    this.contrasenaCorreo.set('');
    this.codigoCorreo.set('');
    this.avisoCorreo.set('');
    this.errorCorreo.set('');
  }

  /** Paso 1: código al correo de hoy. */
  async pedirCodigoDelCorreoActual(): Promise<void> {
    this.errorCorreo.set('');
    this.guardando.set(true);
    try {
      const r = await this.api.pedirCambioDeCorreo(this.contrasenaCorreo(), this.correoNuevo().trim());
      this.solicitudCorreo.set(r.solicitudId);
      // El aviso queda para los reenvios: el paso ya explica a donde fue el codigo
      this.avisoCorreo.set('');
      if (r.codigo) this.codigoCorreo.set(r.codigo);   // modo de pruebas sin correo
      this.pasoCorreo.set('actual');
      this.arrancarReloj();
    } catch (e) {
      this.errorCorreo.set(mensajeDeError(e));
    } finally {
      this.guardando.set(false);
    }
  }

  /** Paso 2: se confirma ese código y sale otro al correo nuevo. */
  async confirmarCodigoDelCorreoActual(): Promise<void> {
    this.errorCorreo.set('');
    this.guardando.set(true);
    try {
      const r = await this.api.confirmarCorreoActual(this.solicitudCorreo()!, this.codigoCorreo().trim());
      this.avisoCorreo.set('');
      this.codigoCorreo.set(r.codigo ?? '');
      this.pasoCorreo.set('nuevo');
      this.arrancarReloj();
    } catch (e) {
      this.errorCorreo.set(mensajeDeError(e));
    } finally {
      this.guardando.set(false);
    }
  }

  /** Paso 3: con el código del correo nuevo, el cambio se aplica. */
  async confirmarCodigoDelCorreoNuevo(): Promise<void> {
    this.errorCorreo.set('');
    this.guardando.set(true);
    try {
      await this.api.confirmarCorreoNuevo(this.solicitudCorreo()!, this.codigoCorreo().trim());
      this.pasoCorreo.set('cerrado');
      this.detenerReloj();
      this.aviso.set('Tu correo quedó cambiado. A partir de ahora entras con el nuevo.');
    } catch (e) {
      this.errorCorreo.set(mensajeDeError(e));
    } finally {
      this.guardando.set(false);
    }
  }

  // =====================================================================
  // Cancelación: dar de baja la cuenta
  // =====================================================================

  abrirBaja(): void {
    this.pasoBaja.set('contrasena');
    this.contrasenaBaja.set('');
    this.codigoBaja.set('');
    this.avisoBaja.set('');
    this.errorBaja.set('');
  }

  /** Paso 1: con la contraseña correcta se manda el código al correo. */
  async pedirCodigoDeBaja(): Promise<void> {
    this.errorBaja.set('');
    this.guardando.set(true);
    try {
      const r = await this.api.pedirCodigoDeBaja(this.contrasenaBaja());
      this.solicitudBaja.set(r.solicitudId);
      this.avisoBaja.set('');
      if (r.codigo) this.codigoBaja.set(r.codigo);
      this.pasoBaja.set('codigo');
      this.arrancarReloj();
    } catch (e) {
      this.errorBaja.set(mensajeDeError(e));
    } finally {
      this.guardando.set(false);
    }
  }

  /** Paso 2: con la contraseña y el código, la cuenta se borra. */
  async confirmarBaja(): Promise<void> {
    this.errorBaja.set('');
    this.guardando.set(true);
    try {
      const r = await this.api.confirmarBaja(
        this.solicitudBaja()!, this.codigoBaja().trim(), this.contrasenaBaja());
      this.detenerReloj();
      this.pasoBaja.set('cerrado');
      this.bajaLista.set(r.mensaje);
      setTimeout(() => void this.router.navigate(['/']), 3500);
    } catch (e) {
      this.errorBaja.set(mensajeDeError(e));
      this.guardando.set(false);
    }
  }

  // =====================================================================
  // Comunes a los dos trámites
  // =====================================================================

  async reenviar(cual: 'correo' | 'baja'): Promise<void> {
    const id = cual === 'correo' ? this.solicitudCorreo() : this.solicitudBaja();
    if (id === null || this.segundosParaReenviar() > 0) return;
    try {
      const r = await this.api.reenviarCodigoDeCuenta(id);
      if (cual === 'correo') {
        this.avisoCorreo.set(r.mensaje);
        if (r.codigo) this.codigoCorreo.set(r.codigo);
      } else {
        this.avisoBaja.set(r.mensaje);
        if (r.codigo) this.codigoBaja.set(r.codigo);
      }
      this.arrancarReloj();
    } catch (e) {
      if (cual === 'correo') this.errorCorreo.set(mensajeDeError(e));
      else this.errorBaja.set(mensajeDeError(e));
    }
  }

  /** Deja el trámite sin efecto: en la cuenta no cambia nada. */
  async cancelarTramite(cual: 'correo' | 'baja'): Promise<void> {
    this.detenerReloj();
    try {
      await this.api.cancelarTramite();
    } catch { /* si ya venció, da igual */ }
    if (cual === 'correo') {
      this.pasoCorreo.set('cerrado');
      this.solicitudCorreo.set(null);
    } else {
      this.pasoBaja.set('cerrado');
      this.solicitudBaja.set(null);
    }
  }

  private arrancarReloj(): void {
    this.detenerReloj();
    this.segundosParaReenviar.set(60);
    this.reloj = setInterval(() => {
      this.segundosParaReenviar.update((s) => Math.max(0, s - 1));
      if (this.segundosParaReenviar() === 0) this.detenerReloj();
    }, 1000);
  }

  private detenerReloj(): void {
    if (this.reloj) clearInterval(this.reloj);
    this.reloj = null;
  }
}
