import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, mensajeDeError } from '../../api/api.service';
import { GrupoCarta, Restaurante, Zona } from '../../api/modelos';

/** Portada: quién es el restaurante, sus áreas y unos platillos para abrir el apetito. */
@Component({
  selector: 'app-inicio',
  imports: [RouterLink],
  templateUrl: './inicio.html',
  styleUrl: './inicio.css',
})
export class Inicio {
  private readonly api = inject(ApiService);

  readonly restaurante = signal<Restaurante | null>(null);
  readonly zonas = signal<Zona[]>([]);
  readonly destacados = signal<GrupoCarta[]>([]);
  readonly error = signal('');

  /** Consulta de una reserva con el código, sin tener que iniciar sesión. */
  readonly codigo = signal('');
  readonly buscando = signal(false);
  readonly encontrada = signal<Record<string, any> | null>(null);
  readonly errorCodigo = signal('');

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    try {
      const [restaurante, zonas, carta] = await Promise.all([
        this.api.restaurante(),
        this.api.zonas(),
        this.api.carta(),
      ]);
      this.restaurante.set(restaurante);
      this.zonas.set(zonas.filter((z) => z.reservable));
      // Dos platillos de cada grupo, para dar una idea de la carta
      this.destacados.set(carta.map((g) => ({ ...g, platillos: g.platillos.slice(0, 2) })));
    } catch (e) {
      this.error.set(mensajeDeError(e));
    }
  }

  /** Las fotos se guardan en parámetros, separadas por comas. */
  get fotos(): string[] {
    const texto = this.restaurante()?.fotos ?? '';
    return texto.split(',').map((f) => f.trim()).filter((f) => f.length > 0);
  }

  async buscar(): Promise<void> {
    const codigo = this.codigo().trim();
    if (!codigo) return;
    this.buscando.set(true);
    this.errorCodigo.set('');
    this.encontrada.set(null);
    try {
      this.encontrada.set(await this.api.porCodigo(codigo));
    } catch (e) {
      this.errorCodigo.set(mensajeDeError(e));
    } finally {
      this.buscando.set(false);
    }
  }
}
