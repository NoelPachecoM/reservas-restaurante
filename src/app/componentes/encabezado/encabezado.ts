import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { ApiService } from '../../api/api.service';

/** Barra de arriba: el nombre del restaurante y los enlaces según quién entró. */
@Component({
  selector: 'app-encabezado',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './encabezado.html',
  styleUrl: './encabezado.css',
})
export class Encabezado {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly abierto = signal(false);
  readonly usuario = this.api.usuario;
  readonly nombre = signal('Mesa Nuestra');
  readonly iniciales = computed(() => {
    const partes = (this.usuario()?.nombre ?? '').trim().split(/\s+/);
    return partes.slice(0, 2).map((p) => p.charAt(0).toUpperCase()).join('') || '?';
  });

  constructor() {
    this.api.restaurante().then((r) => this.nombre.set(r.nombre)).catch(() => undefined);
  }

  alternar(): void {
    this.abierto.update((v) => !v);
  }

  cerrar(): void {
    this.abierto.set(false);
  }

  salir(): void {
    this.api.salir();
    this.cerrar();
    void this.router.navigate(['/']);
  }
}
