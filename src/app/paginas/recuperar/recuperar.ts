import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiService, mensajeDeError } from '../../api/api.service';

/** Olvidé mi contraseña: primero llega un código al correo y luego se cambia. */
@Component({
  selector: 'app-recuperar',
  imports: [RouterLink],
  templateUrl: './recuperar.html',
})
export class Recuperar {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly paso = signal(1);
  readonly correo = signal('');
  readonly codigo = signal('');
  readonly contrasena = signal('');
  readonly aviso = signal('');
  readonly error = signal('');
  readonly trabajando = signal(false);

  async pedir(): Promise<void> {
    this.error.set('');
    this.trabajando.set(true);
    try {
      const r = await this.api.pedirCodigo(this.correo().trim());
      this.aviso.set(r.mensaje);
      this.paso.set(2);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.trabajando.set(false);
    }
  }

  async cambiar(): Promise<void> {
    this.error.set('');
    this.trabajando.set(true);
    try {
      const r = await this.api.cambiarConCodigo(this.correo().trim(), this.codigo().trim(), this.contrasena());
      this.aviso.set(r.mensaje);
      this.paso.set(3);
      setTimeout(() => void this.router.navigate(['/entrar']), 2500);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.trabajando.set(false);
    }
  }
}
