import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService, mensajeDeError } from '../../api/api.service';
import { Croquis } from '../../componentes/croquis/croquis';
import { CuentaDeMesa, GrupoCarta, MesaCroquis, Reserva, Zona } from '../../api/modelos';

interface LineaElegida {
  idPlatillo: number;
  nombre: string;
  precio: number;
  cantidad: number;
  notas: string;
}

/** Una reserva con todo: dónde quedó la mesa, el pedido y la cuenta. */
@Component({
  selector: 'app-reserva-detalle',
  imports: [RouterLink, Croquis],
  templateUrl: './reserva-detalle.html',
  styleUrl: './reserva-detalle.css',
})
export class ReservaDetalle {
  private readonly api = inject(ApiService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly id = Number(this.ruta.snapshot.paramMap.get('id'));

  readonly reserva = signal<Reserva | null>(null);
  readonly cuenta = signal<CuentaDeMesa | null>(null);
  readonly zonas = signal<Zona[]>([]);
  readonly mesas = signal<MesaCroquis[]>([]);
  readonly carta = signal<GrupoCarta[]>([]);
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly aviso = signal('');
  readonly pidiendo = signal(false);
  readonly agregando = signal(false);
  readonly elegidos = signal<LineaElegida[]>([]);

  readonly total = computed(() => this.elegidos().reduce((s, l) => s + l.precio * l.cantidad, 0));
  readonly sePuedePedir = computed(() => {
    const r = this.reserva();
    return r !== null && (r.estado === 'CONFIRMADA' || r.estado === 'EN_MESA');
  });

  constructor() {
    void this.cargar();
  }

  private async cargar(): Promise<void> {
    this.cargando.set(true);
    try {
      const reserva = await this.api.reserva(this.id);
      this.reserva.set(reserva);
      const [cuenta, zonas, mesas] = await Promise.all([
        this.api.cuentaDeMiReserva(this.id),
        this.api.zonas(),
        this.api.croquis(reserva.fecha, reserva.hora.slice(0, 5)),
      ]);
      this.cuenta.set(cuenta);
      this.zonas.set(zonas);
      // La mesa de esta reserva se marca como propia en el croquis
      this.mesas.set(mesas.map((m) => (m.id === reserva.idMesa ? { ...m, estado: 'PROPIA' } : m)));
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.cargando.set(false);
    }
  }

  async abrirCarta(): Promise<void> {
    this.agregando.set(true);
    if (!this.carta().length) {
      try {
        this.carta.set(await this.api.carta());
      } catch (e) {
        this.error.set(mensajeDeError(e));
      }
    }
  }

  cantidadDe(idPlatillo: number): number {
    return this.elegidos().find((l) => l.idPlatillo === idPlatillo)?.cantidad ?? 0;
  }

  notasDe(idPlatillo: number): string {
    return this.elegidos().find((l) => l.idPlatillo === idPlatillo)?.notas ?? '';
  }

  sumar(p: { id: number; nombre: string; precio: number }, cuantos: number): void {
    this.elegidos.update((lista) => {
      const copia = [...lista];
      const i = copia.findIndex((l) => l.idPlatillo === p.id);
      if (i === -1) {
        if (cuantos > 0) {
          copia.push({ idPlatillo: p.id, nombre: p.nombre, precio: p.precio, cantidad: cuantos, notas: '' });
        }
        return copia;
      }
      const cantidad = copia[i].cantidad + cuantos;
      if (cantidad <= 0) copia.splice(i, 1);
      else copia[i] = { ...copia[i], cantidad };
      return copia;
    });
  }

  anotarNota(idPlatillo: number, texto: string): void {
    this.elegidos.update((lista) =>
      lista.map((l) => (l.idPlatillo === idPlatillo ? { ...l, notas: texto } : l)));
  }

  async mandarPedido(): Promise<void> {
    if (!this.elegidos().length) return;
    this.pidiendo.set(true);
    this.error.set('');
    try {
      await this.api.pedir(this.id, this.elegidos().map((l) => ({
        idPlatillo: l.idPlatillo, cantidad: l.cantidad, notas: l.notas.trim() || null,
      })));
      this.elegidos.set([]);
      this.agregando.set(false);
      this.aviso.set('Tu pedido llegó a la cocina.');
      this.cuenta.set(await this.api.cuentaDeMiReserva(this.id));
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.pidiendo.set(false);
    }
  }

  async borrarPedido(idPedido: number): Promise<void> {
    this.error.set('');
    try {
      await this.api.borrarPedido(idPedido);
      this.aviso.set('Se quitó ese pedido.');
      this.cuenta.set(await this.api.cuentaDeMiReserva(this.id));
    } catch (e) {
      this.error.set(mensajeDeError(e));
    }
  }

  clasePedido(estado: string): string {
    if (estado === 'PENDIENTE') return 'chip chip-aviso';
    if (estado === 'PREPARANDO') return 'chip chip-principal';
    if (estado === 'SERVIDO') return 'chip chip-ok';
    return 'chip chip-malo';
  }
}
