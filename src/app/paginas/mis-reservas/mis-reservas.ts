import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, mensajeDeError } from '../../api/api.service';
import { Reserva } from '../../api/modelos';

/** Las reservas de la persona: las que vienen y el historial. */
@Component({
  selector: 'app-mis-reservas',
  imports: [RouterLink],
  templateUrl: './mis-reservas.html',
  styleUrl: './mis-reservas.css',
})
export class MisReservas {
  private readonly api = inject(ApiService);

  readonly reservas = signal<Reserva[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly aviso = signal('');
  readonly porCancelar = signal<Reserva | null>(null);

  readonly proximas = computed(() =>
    this.reservas()
      .filter((r) => !r.pasada && (r.estado === 'CONFIRMADA' || r.estado === 'EN_MESA'))
      .sort((a, b) => (a.fecha + a.hora).localeCompare(b.fecha + b.hora)));

  readonly pasadas = computed(() =>
    this.reservas().filter((r) => r.pasada || (r.estado !== 'CONFIRMADA' && r.estado !== 'EN_MESA')));

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    try {
      this.reservas.set(await this.api.misReservas());
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.cargando.set(false);
    }
  }

  async cancelar(): Promise<void> {
    const reserva = this.porCancelar();
    if (!reserva) return;
    this.error.set('');
    try {
      await this.api.cancelarReserva(reserva.id);
      this.porCancelar.set(null);
      this.aviso.set('Tu reserva quedó cancelada. La mesa ya está libre para alguien más.');
      await this.cargar();
    } catch (e) {
      this.error.set(mensajeDeError(e));
      this.porCancelar.set(null);
    }
  }

  claseEstado(r: Reserva): string {
    if (r.estado === 'CONFIRMADA') return 'chip chip-ok';
    if (r.estado === 'EN_MESA') return 'chip chip-principal';
    if (r.estado === 'FINALIZADA') return 'chip';
    return 'chip chip-malo';
  }
}
