import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, mensajeDeError } from '../../api/api.service';
import { Pedido } from '../../api/modelos';

/** Pantalla de cocina: los pedidos que faltan, del más viejo al más nuevo. */
@Component({
  selector: 'app-cocina',
  imports: [RouterLink],
  templateUrl: './cocina.html',
  styleUrl: './cocina.css',
})
export class Cocina implements OnDestroy {
  private readonly api = inject(ApiService);

  readonly pedidos = signal<Pedido[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  private refresco: ReturnType<typeof setInterval> | null = null;

  readonly pendientes = computed(() => this.pedidos().filter((p) => p.estado === 'PENDIENTE'));
  readonly enPreparacion = computed(() => this.pedidos().filter((p) => p.estado === 'PREPARANDO'));

  constructor() {
    void this.cargar();
    // La cocina se actualiza sola cada 20 segundos
    this.refresco = setInterval(() => void this.cargar(), 20_000);
  }

  ngOnDestroy(): void {
    if (this.refresco) clearInterval(this.refresco);
  }

  async cargar(): Promise<void> {
    try {
      this.pedidos.set(await this.api.cocina());
      this.error.set('');
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.cargando.set(false);
    }
  }

  async cambiar(pedido: Pedido, valor: string): Promise<void> {
    this.error.set('');
    try {
      await this.api.cambiarEstadoPedido(pedido.id, valor);
      await this.cargar();
    } catch (e) {
      this.error.set(mensajeDeError(e));
    }
  }

  /** Cuánto lleva esperando el pedido, en minutos. */
  minutos(pedido: Pedido): number {
    const hecho = new Date(pedido.fecha).getTime();
    return Math.max(0, Math.floor((Date.now() - hecho) / 60000));
  }

  hora(pedido: Pedido): string {
    return pedido.fecha.slice(11, 16);
  }
}
