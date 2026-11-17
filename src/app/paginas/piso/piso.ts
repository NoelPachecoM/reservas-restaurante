import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, mensajeDeError } from '../../api/api.service';
import { Croquis } from '../../componentes/croquis/croquis';
import { Telefono } from '../../componentes/telefono.directive';
import { CuentaDeMesa, GrupoCarta, MesaCroquis, Reserva, Restaurante, Zona } from '../../api/modelos';

interface LineaElegida {
  idPlatillo: number;
  nombre: string;
  precio: number;
  cantidad: number;
  notas: string;
}

/**
 * Vista de piso: lo que el mesero usa durante el turno. El croquis en vivo, la agenda
 * del día, marcar llegadas y salidas, mover de mesa, levantar pedidos y la cuenta.
 */
@Component({
  selector: 'app-piso',
  imports: [RouterLink, Croquis, Telefono],
  templateUrl: './piso.html',
  styleUrl: './piso.css',
})
export class Piso implements OnDestroy {
  private readonly api = inject(ApiService);

  readonly restaurante = signal<Restaurante | null>(null);
  readonly zonas = signal<Zona[]>([]);
  readonly mesas = signal<MesaCroquis[]>([]);
  readonly agenda = signal<Reserva[]>([]);
  readonly fecha = signal('');
  readonly hora = signal('');
  readonly cargando = signal(true);
  readonly error = signal('');
  readonly aviso = signal('');

  /** Reserva abierta en el panel de la derecha. */
  readonly elegida = signal<Reserva | null>(null);
  readonly cuenta = signal<CuentaDeMesa | null>(null);
  readonly moviendo = signal(false);

  /** Alta rápida para quien llega sin reservar. */
  readonly nuevaEnMesa = signal<MesaCroquis | null>(null);
  readonly nombre = signal('');
  readonly telefono = signal('');
  readonly personas = signal(2);
  readonly guardando = signal(false);

  /** Levantar un pedido. */
  readonly pidiendo = signal(false);
  readonly carta = signal<GrupoCarta[]>([]);
  readonly elegidos = signal<LineaElegida[]>([]);

  readonly codigo = signal('');

  private refresco: ReturnType<typeof setInterval> | null = null;

  readonly total = computed(() => this.elegidos().reduce((s, l) => s + l.precio * l.cantidad, 0));

  readonly pendientes = computed(() => this.agenda().filter((r) => r.estado === 'CONFIRMADA'));
  readonly enMesa = computed(() => this.agenda().filter((r) => r.estado === 'EN_MESA'));
  readonly retrasados = computed(() => this.agenda().filter((r) => r.tarde));
  readonly comensalesAhora = computed(() =>
    this.enMesa().reduce((suma, r) => suma + r.personas, 0));

  constructor() {
    void this.arrancar();
    // El piso cambia solo: se vuelve a leer cada 45 segundos
    this.refresco = setInterval(() => void this.recargar(), 45_000);
  }

  ngOnDestroy(): void {
    if (this.refresco) clearInterval(this.refresco);
  }

  private async arrancar(): Promise<void> {
    try {
      const [restaurante, zonas, hoy] = await Promise.all([
        this.api.restaurante(), this.api.zonas(), this.api.hoy(),
      ]);
      this.restaurante.set(restaurante);
      this.zonas.set(zonas);
      this.fecha.set(hoy.fecha);
      this.hora.set(this.franjaDeAhora(restaurante));
      await this.recargar();
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.cargando.set(false);
    }
  }

  /** La franja que está corriendo en este momento (o la primera del día). */
  private franjaDeAhora(restaurante: Restaurante): string {
    const ahora = new Date();
    const minutos = ahora.getHours() * 60 + ahora.getMinutes();
    let elegida = restaurante.franjas[0] ?? '13:00';
    for (const f of restaurante.franjas) {
      const [h, m] = f.split(':').map(Number);
      if (h * 60 + m <= minutos) elegida = f;
    }
    return elegida.slice(0, 5);
  }

  async recargar(): Promise<void> {
    this.error.set('');
    try {
      const [mesas, agenda] = await Promise.all([
        this.api.croquisDePiso(this.fecha(), this.hora()),
        this.api.agenda(this.fecha()),
      ]);
      this.mesas.set(mesas);
      this.agenda.set(agenda);
      // Si había una reserva abierta, se refresca con los datos nuevos
      const abierta = this.elegida();
      if (abierta) {
        const nueva = agenda.find((r) => r.id === abierta.id);
        if (nueva) this.elegida.set(nueva);
      }
    } catch (e) {
      this.error.set(mensajeDeError(e));
    }
  }

  // ---------------- El croquis ----------------

  async tocarMesa(mesa: MesaCroquis): Promise<void> {
    this.nuevaEnMesa.set(null);
    // Si se está moviendo una reserva, esta mesa es el destino
    if (this.moviendo()) {
      await this.moverAqui(mesa);
      return;
    }
    if (mesa.idReserva) {
      const reserva = this.agenda().find((r) => r.id === mesa.idReserva);
      if (reserva) await this.abrir(reserva);
      return;
    }
    if (mesa.estado === 'LIBRE') {
      this.elegida.set(null);
      this.nuevaEnMesa.set(mesa);
      this.personas.set(Math.min(2, mesa.capacidad));
    }
  }

  async abrir(reserva: Reserva): Promise<void> {
    this.elegida.set(reserva);
    this.nuevaEnMesa.set(null);
    this.pidiendo.set(false);
    this.elegidos.set([]);
    try {
      this.cuenta.set(await this.api.cuentaDeMesa(reserva.id));
    } catch (e) {
      this.error.set(mensajeDeError(e));
    }
  }

  cerrarPanel(): void {
    this.elegida.set(null);
    this.cuenta.set(null);
    this.moviendo.set(false);
    this.pidiendo.set(false);
  }

  // ---------------- Estados ----------------

  async cambiarEstado(valor: string): Promise<void> {
    const reserva = this.elegida();
    if (!reserva) return;
    this.error.set('');
    try {
      const nueva = await this.api.cambiarEstadoReserva(reserva.id, valor);
      this.elegida.set(nueva);
      this.aviso.set(`Mesa ${nueva.idMesa}: ${nueva.estadoNombre.toLowerCase()}.`);
      await this.recargar();
      if (valor === 'FINALIZADA' || valor === 'NO_SE_PRESENTO' || valor === 'CANCELADA') {
        this.cerrarPanel();
      }
    } catch (e) {
      this.error.set(mensajeDeError(e));
    }
  }

  private async moverAqui(mesa: MesaCroquis): Promise<void> {
    const reserva = this.elegida();
    if (!reserva) return;
    this.error.set('');
    try {
      const nueva = await this.api.moverDeMesa(reserva.id, mesa.id);
      this.elegida.set(nueva);
      this.moviendo.set(false);
      this.aviso.set(`${nueva.cliente} pasó a la mesa ${nueva.idMesa}.`);
      await this.recargar();
    } catch (e) {
      this.error.set(mensajeDeError(e));
      this.moviendo.set(false);
    }
  }

  // ---------------- Alta rápida ----------------

  async guardarNueva(): Promise<void> {
    const mesa = this.nuevaEnMesa();
    if (!mesa) return;
    this.guardando.set(true);
    this.error.set('');
    try {
      const reserva = await this.api.reservarEnPiso({
        fecha: this.fecha(),
        hora: this.hora(),
        personas: this.personas(),
        idMesa: mesa.id,
        nombre: this.nombre().trim(),
        telefono: this.telefono().trim() || undefined,
      });
      this.nuevaEnMesa.set(null);
      this.nombre.set('');
      this.telefono.set('');
      this.aviso.set(`Mesa ${reserva.idMesa} asignada a ${reserva.cliente}.`);
      await this.recargar();
      await this.abrir(reserva);
      // Quien llega sin reservar ya está en la mesa
      await this.cambiarEstado('EN_MESA');
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.guardando.set(false);
    }
  }

  // ---------------- Pedidos ----------------

  async abrirCarta(): Promise<void> {
    this.pidiendo.set(true);
    if (!this.carta().length) {
      try {
        this.carta.set(await this.api.carta());
      } catch (e) {
        this.error.set(mensajeDeError(e));
      }
    }
  }

  cantidadDe(id: number): number {
    return this.elegidos().find((l) => l.idPlatillo === id)?.cantidad ?? 0;
  }

  notasDe(id: number): string {
    return this.elegidos().find((l) => l.idPlatillo === id)?.notas ?? '';
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

  anotarNota(id: number, texto: string): void {
    this.elegidos.update((lista) =>
      lista.map((l) => (l.idPlatillo === id ? { ...l, notas: texto } : l)));
  }

  async mandarPedido(): Promise<void> {
    const reserva = this.elegida();
    if (!reserva || !this.elegidos().length) return;
    this.error.set('');
    try {
      await this.api.pedirEnPiso(reserva.id, this.elegidos().map((l) => ({
        idPlatillo: l.idPlatillo, cantidad: l.cantidad, notas: l.notas.trim() || null,
      })));
      this.elegidos.set([]);
      this.pidiendo.set(false);
      this.aviso.set('Pedido enviado a la cocina.');
      this.cuenta.set(await this.api.cuentaDeMesa(reserva.id));
    } catch (e) {
      this.error.set(mensajeDeError(e));
    }
  }

  // ---------------- Buscar por código ----------------

  async buscar(): Promise<void> {
    const codigo = this.codigo().trim();
    if (!codigo) return;
    this.error.set('');
    try {
      const reserva = await this.api.buscarPorCodigo(codigo);
      this.fecha.set(reserva.fecha);
      this.hora.set(reserva.hora.slice(0, 5));
      await this.recargar();
      await this.abrir(reserva);
      this.codigo.set('');
    } catch (e) {
      this.error.set(mensajeDeError(e));
    }
  }

  async cambiarMomento(): Promise<void> {
    this.cerrarPanel();
    await this.recargar();
  }

  claseEstado(r: Reserva): string {
    if (r.estado === 'EN_MESA') return 'chip chip-principal';
    if (r.estado === 'CONFIRMADA') return r.tarde ? 'chip chip-aviso' : 'chip chip-ok';
    if (r.estado === 'FINALIZADA') return 'chip';
    return 'chip chip-malo';
  }
}
