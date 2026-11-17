import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, mensajeDeError } from '../../api/api.service';
import { GrupoCarta, Platillo } from '../../api/modelos';

/** La carta completa, con filtro por etiquetas y aviso de alérgenos. */
@Component({
  selector: 'app-carta',
  imports: [RouterLink],
  templateUrl: './carta.html',
  styleUrl: './carta.css',
})
export class Carta {
  private readonly api = inject(ApiService);

  readonly grupos = signal<GrupoCarta[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly etiqueta = signal<string>('');
  readonly detalle = signal<Platillo | null>(null);

  /** Todas las etiquetas que aparecen en la carta (vegetariano, picante, sin gluten…). */
  readonly etiquetas = computed(() => {
    const juntas = new Set<string>();
    for (const g of this.grupos()) {
      for (const p of g.platillos) {
        for (const e of (p.etiquetas ?? '').split(',')) {
          const limpia = e.trim();
          if (limpia) juntas.add(limpia);
        }
      }
    }
    return [...juntas].sort();
  });

  /** La carta filtrada por la etiqueta elegida. */
  readonly filtrada = computed(() => {
    const etiqueta = this.etiqueta();
    if (!etiqueta) return this.grupos();
    return this.grupos()
      .map((g) => ({ ...g, platillos: g.platillos.filter((p) => this.tiene(p, etiqueta)) }))
      .filter((g) => g.platillos.length > 0);
  });

  constructor() {
    this.api.carta()
      .then((c) => this.grupos.set(c))
      .catch((e) => this.error.set(mensajeDeError(e)))
      .finally(() => this.cargando.set(false));
  }

  tiene(platillo: Platillo, etiqueta: string): boolean {
    return (platillo.etiquetas ?? '').split(',').map((e) => e.trim()).includes(etiqueta);
  }

  listaDe(texto: string | null): string[] {
    return (texto ?? '').split(',').map((t) => t.trim()).filter((t) => t.length > 0);
  }

  elegirEtiqueta(etiqueta: string): void {
    this.etiqueta.update((actual) => (actual === etiqueta ? '' : etiqueta));
  }
}
