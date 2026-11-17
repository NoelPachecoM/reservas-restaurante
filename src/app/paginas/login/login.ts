import { Component, OnDestroy, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService, mensajeDeError } from '../../api/api.service';
import { Telefono } from '../../componentes/telefono.directive';

/**
 * Entrar o crear cuenta.
 *
 * El registro va en dos pasos: primero se mandan los datos y llega un código de 6 dígitos
 * al correo; la cuenta se crea hasta que ese código se escribe bien. Así nadie se registra
 * con un correo que no es suyo. El personal además necesita el código de empleado.
 */
@Component({
  selector: 'app-login',
  imports: [RouterLink, Telefono],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);

  readonly modo = signal<'entrar' | 'registro'>('entrar');
  /** En el registro: 'datos' pide el formulario, 'codigo' pide lo que llegó al correo. */
  readonly paso = signal<'datos' | 'codigo'>('datos');

  readonly comoEmpleado = signal(false);
  readonly correo = signal('');
  readonly contrasena = signal('');
  readonly nombre = signal('');
  readonly telefono = signal('');
  readonly codigoEmpleado = signal('');
  readonly codigo = signal('');
  readonly solicitudId = signal<number | null>(null);
  readonly aviso = signal('');
  readonly error = signal('');
  readonly trabajando = signal(false);
  readonly segundosParaReenviar = signal(0);

  private reloj: ReturnType<typeof setInterval> | null = null;
  private readonly volver = this.ruta.snapshot.queryParamMap.get('volver');

  /** Las cuentas de prueba solo se enseñan si el servidor esta en modo demostracion. */
  readonly demo = signal(false);

  constructor() {
    this.api.restaurante()
      .then((r) => this.demo.set(r.demo === true))
      .catch(() => undefined);
  }

  ngOnDestroy(): void {
    this.detenerReloj();
  }

  // ---------------- Entrar ----------------

  async entrar(): Promise<void> {
    this.error.set('');
    this.trabajando.set(true);
    try {
      await this.api.entrar(this.correo().trim(), this.contrasena());
      await this.irADondeIba();
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.trabajando.set(false);
    }
  }

  // ---------------- Registro en dos pasos ----------------

  /** Paso 1: manda los datos y pide el código al correo. */
  async pedirCodigo(): Promise<void> {
    this.error.set('');
    this.trabajando.set(true);
    try {
      const r = await this.api.solicitarRegistro({
        nombre: this.nombre().trim(),
        telefono: this.telefono().trim(),
        correo: this.correo().trim(),
        contrasena: this.contrasena(),
        codigoEmpleado: this.comoEmpleado() ? this.codigoEmpleado().trim() : undefined,
      }, this.comoEmpleado());
      this.solicitudId.set(r.solicitudId);
      // El aviso queda para los reenvios: la pantalla del codigo ya explica lo demas
      this.aviso.set('');
      if (r.codigo) this.codigo.set(r.codigo);   // modo de pruebas sin correo
      this.paso.set('codigo');
      this.arrancarReloj();
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.trabajando.set(false);
    }
  }

  /** Paso 2: con el código correcto se crea la cuenta y se entra. */
  async confirmar(): Promise<void> {
    const id = this.solicitudId();
    if (id === null) return;
    this.error.set('');
    this.trabajando.set(true);
    try {
      await this.api.confirmarRegistro(id, this.codigo().trim());
      await this.irADondeIba();
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.trabajando.set(false);
    }
  }

  async reenviar(): Promise<void> {
    const id = this.solicitudId();
    if (id === null || this.segundosParaReenviar() > 0) return;
    this.error.set('');
    this.trabajando.set(true);
    try {
      const r = await this.api.reenviarCodigo(id);
      this.aviso.set(r.mensaje);
      if (r.codigo) this.codigo.set(r.codigo);
      this.arrancarReloj();
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.trabajando.set(false);
    }
  }

  /** Regresar al formulario para corregir el correo. */
  corregirDatos(): void {
    this.paso.set('datos');
    this.codigo.set('');
    this.aviso.set('');
    this.error.set('');
    this.detenerReloj();
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

  private async irADondeIba(): Promise<void> {
    const usuario = this.api.usuario();
    if (usuario?.debeCambiarContrasena) {
      await this.router.navigate(['/mi-cuenta'], { queryParams: { cambiar: 1 } });
      return;
    }
    await this.router.navigateByUrl(this.volver ?? (usuario?.esPersonal ? '/piso' : '/mis-reservas'));
  }

  cambiarModo(modo: 'entrar' | 'registro'): void {
    this.modo.set(modo);
    this.paso.set('datos');
    this.error.set('');
    this.aviso.set('');
  }

  get listoParaEntrar(): boolean {
    return !this.trabajando() && !!this.correo().trim() && !!this.contrasena();
  }

  get listoParaPedirCodigo(): boolean {
    if (this.trabajando()) return false;
    if (this.nombre().trim().length < 3) return false;
    if (this.telefono().trim().length !== 10) return false;
    if (!this.correo().trim()) return false;
    if (this.contrasena().length < 8) return false;
    return !this.comoEmpleado() || this.codigoEmpleado().trim().length > 3;
  }
}
