import { Component, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService, mensajeDeError } from '../../api/api.service';
import { Croquis } from '../../componentes/croquis/croquis';
import { Telefono } from '../../componentes/telefono.directive';
import { Dia, GrupoCarta, Horario, MesaCroquis, Reserva, Restaurante, Zona } from '../../api/modelos';

interface Casilla {
  fecha: string;
  dia: number;
  estado: Dia['estado'] | 'VACIO';
}

interface LineaElegida {
  idPlatillo: number;
  nombre: string;
  precio: number;
  cantidad: number;
  notas: string;
}

const DIAS_SEMANA = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto',
  'septiembre', 'octubre', 'noviembre', 'diciembre'];

/**
 * Reservar en tres pasos: cuándo y cuántos, dónde (el croquis) y confirmar.
 * Con /reservar/:id la misma pantalla sirve para cambiar una reserva que ya existe.
 */
@Component({
  selector: 'app-reservar',
  imports: [RouterLink, Croquis, Telefono],
  templateUrl: './reservar.html',
  styleUrl: './reservar.css',
})
export class Reservar implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);

  readonly diasSemana = DIAS_SEMANA;
  readonly usuario = this.api.usuario;

  // ---------------- Estado general ----------------
  readonly paso = signal(1);
  readonly restaurante = signal<Restaurante | null>(null);
  readonly zonas = signal<Zona[]>([]);
  readonly error = signal('');
  readonly guardando = signal(false);
  readonly listaDeMes = signal<Dia[]>([]);
  readonly cargandoMes = signal(false);

  /** Reserva que se está cambiando (null cuando es una reserva nueva). */
  readonly editando = signal<Reserva | null>(null);
  readonly lista = signal<Reserva | null>(null); // reserva ya confirmada

  // ---------------- Paso 1 ----------------
  readonly personas = signal(2);
  readonly fecha = signal('');
  readonly hora = signal('');
  readonly horarios = signal<Horario[]>([]);
  readonly cargandoHorarios = signal(false);
  readonly avisoEspera = signal('');
  readonly mesVisible = signal(new Date());
  readonly hoy = signal('');

  // ---------------- Paso 2 ----------------
  readonly mesas = signal<MesaCroquis[]>([]);
  readonly cargandoCroquis = signal(false);
  readonly mesa = signal<number | null>(null);
  readonly expira = signal<Date | null>(null);
  readonly segundos = signal(0);
  readonly zonaFiltro = signal<number | null>(null);
  private reloj: ReturnType<typeof setInterval> | null = null;

  // ---------------- Paso 3 ----------------
  readonly ocasion = signal('');
  readonly notas = signal('');
  readonly conPedido = signal(false);
  readonly carta = signal<GrupoCarta[]>([]);
  readonly elegidos = signal<LineaElegida[]>([]);

  // Para quien todavía no tiene cuenta.
  // El registro va en dos pasos: primero los datos, luego el código que llega al correo.
  readonly modoCuenta = signal<'registro' | 'entrar'>('registro');
  readonly pasoCuenta = signal<'datos' | 'codigo'>('datos');
  readonly nombre = signal('');
  readonly telefono = signal('');
  readonly correo = signal('');
  readonly contrasena = signal('');
  readonly codigoCorreo = signal('');
  readonly solicitudId = signal<number | null>(null);
  readonly avisoCuenta = signal('');
  readonly segundosParaReenviar = signal(0);
  private relojCodigo: ReturnType<typeof setInterval> | null = null;

  readonly totalPedido = computed(() =>
    this.elegidos().reduce((suma, l) => suma + l.precio * l.cantidad, 0));

  readonly mesaElegida = computed(() => this.mesas().find((m) => m.id === this.mesa()) ?? null);

  readonly mesasVisibles = computed(() => {
    const zona = this.zonaFiltro();
    const todas = this.mesas();
    return zona === null ? todas : todas.filter((m) => m.idZona === zona);
  });

  /** El calendario del mes que se está viendo, con sus huecos del principio. */
  readonly casillas = computed<Casilla[]>(() => {
    const dias = this.listaDeMes();
    if (!dias.length) return [];
    const primero = new Date(dias[0].fecha + 'T12:00:00');
    // En México la semana empieza en lunes: 0 = lunes
    const hueco = (primero.getDay() + 6) % 7;
    const casillas: Casilla[] = [];
    for (let i = 0; i < hueco; i++) casillas.push({ fecha: '', dia: 0, estado: 'VACIO' });
    for (const d of dias) {
      casillas.push({ fecha: d.fecha, dia: Number(d.fecha.slice(8, 10)), estado: d.estado });
    }
    return casillas;
  });

  readonly tituloMes = computed(() => {
    const d = this.mesVisible();
    return `${MESES[d.getMonth()]} de ${d.getFullYear()}`;
  });

  constructor() {
    void this.arrancar();
    // Cada vez que cambia el mes visible o el número de personas, se repinta el calendario
    effect(() => {
      const mes = this.mesVisible();
      const personas = this.personas();
      void this.cargarMes(mes, personas);
    });
  }

  ngOnDestroy(): void {
    this.detenerReloj();
    this.detenerRelojCodigo();
    // Si la persona se va sin terminar, se libera la mesa que tenía apartada
    if (this.mesa() !== null && !this.lista()) {
      this.api.soltar().catch(() => undefined);
    }
  }

  // =====================================================================
  // Arranque
  // =====================================================================

  private async arrancar(): Promise<void> {
    try {
      const [restaurante, zonas, hoy] = await Promise.all([
        this.api.restaurante(), this.api.zonas(), this.api.hoy(),
      ]);
      this.restaurante.set(restaurante);
      this.zonas.set(zonas);
      this.hoy.set(hoy.fecha);
      this.fecha.set(hoy.fecha);
      this.mesVisible.set(new Date(hoy.fecha + 'T12:00:00'));

      const usuario = this.usuario();
      if (usuario) {
        this.nombre.set(usuario.nombre);
        this.telefono.set(usuario.telefono ?? '');
        this.correo.set(usuario.correo);
      }

      const id = this.ruta.snapshot.paramMap.get('id');
      if (id) await this.cargarParaCambiar(Number(id));
      else await this.cargarHorarios();
    } catch (e) {
      this.error.set(mensajeDeError(e));
    }
  }

  /** Trae la reserva que se va a cambiar y deja la pantalla con sus datos. */
  private async cargarParaCambiar(id: number): Promise<void> {
    const reserva = await this.api.reserva(id);
    this.editando.set(reserva);
    this.personas.set(reserva.personas);
    this.fecha.set(reserva.fecha);
    this.hora.set(reserva.hora.slice(0, 5));
    this.ocasion.set(reserva.ocasion ?? '');
    this.notas.set(reserva.notas ?? '');
    this.mesa.set(reserva.idMesa);
    this.mesVisible.set(new Date(reserva.fecha + 'T12:00:00'));
    await this.cargarHorarios();
    await this.cargarCroquis();
  }

  // =====================================================================
  // Paso 1: cuándo y cuántos
  // =====================================================================

  cambiarPersonas(cuantas: number): void {
    const nuevas = Math.min(20, Math.max(1, cuantas));
    this.personas.set(nuevas);
    // Si la mesa marcada ya no alcanza, se suelta
    const mesa = this.mesaElegida();
    if (mesa && mesa.capacidad < nuevas) this.soltarMesa();
    void this.cargarHorarios();
  }

  private async cargarMes(mes: Date, personas: number): Promise<void> {
    this.cargandoMes.set(true);
    try {
      this.listaDeMes.set(await this.api.dias(mes.getFullYear(), mes.getMonth() + 1, personas));
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.cargandoMes.set(false);
    }
  }

  moverMes(cuantos: number): void {
    const actual = this.mesVisible();
    this.mesVisible.set(new Date(actual.getFullYear(), actual.getMonth() + cuantos, 1));
  }

  /** No se puede ir a un mes anterior al de hoy. */
  get puedeMesAnterior(): boolean {
    const visible = this.mesVisible();
    const hoy = new Date(this.hoy() + 'T12:00:00');
    return visible.getFullYear() > hoy.getFullYear()
      || (visible.getFullYear() === hoy.getFullYear() && visible.getMonth() > hoy.getMonth());
  }

  sePuedeElegir(casilla: Casilla): boolean {
    return casilla.estado === 'LIBRE' || casilla.estado === 'PARCIAL';
  }

  async elegirDia(casilla: Casilla): Promise<void> {
    if (!this.sePuedeElegir(casilla)) return;
    this.fecha.set(casilla.fecha);
    this.hora.set('');
    this.soltarMesa();
    await this.cargarHorarios();
  }

  async cargarHorarios(): Promise<void> {
    if (!this.fecha()) return;
    this.cargandoHorarios.set(true);
    this.error.set('');
    try {
      this.horarios.set(await this.api.horarios(this.fecha(), this.personas()));
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.cargandoHorarios.set(false);
    }
  }

  async elegirHora(horario: Horario): Promise<void> {
    if (!horario.disponible) return;
    this.hora.set(horario.hora.slice(0, 5));
    this.soltarMesa();
  }

  get hayHorarios(): boolean {
    return this.horarios().some((h) => h.disponible);
  }

  async irAlCroquis(): Promise<void> {
    if (!this.hora()) return;
    this.paso.set(2);
    await this.cargarCroquis();
  }

  // =====================================================================
  // Paso 2: el croquis
  // =====================================================================

  async cargarCroquis(): Promise<void> {
    if (!this.fecha() || !this.hora()) return;
    this.cargandoCroquis.set(true);
    try {
      const mesas = await this.api.croquis(this.fecha(), this.hora());
      // Al cambiar una reserva, su propia mesa se puede volver a elegir
      const propia = this.editando()?.idMesa;
      if (propia) {
        const suya = mesas.find((m) => m.id === propia);
        if (suya && suya.estado === 'OCUPADA') suya.estado = 'PROPIA';
      }
      this.mesas.set(mesas);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.cargandoCroquis.set(false);
    }
  }

  /** Toca una mesa: se aparta unos minutos para que nadie más se la gane. */
  async tomarMesa(mesa: MesaCroquis): Promise<void> {
    this.error.set('');
    try {
      const apartado = await this.api.apartar(mesa.id, this.fecha(), this.hora());
      this.mesa.set(mesa.id);
      this.expira.set(new Date(apartado.expira));
      this.arrancarReloj();
    } catch (e) {
      this.error.set(mensajeDeError(e));
      await this.cargarCroquis();   // alguien se adelantó: se repinta el croquis
    }
  }

  soltarMesa(): void {
    if (this.mesa() === null) return;
    this.mesa.set(null);
    this.expira.set(null);
    this.detenerReloj();
    this.api.soltar().catch(() => undefined);
  }

  private arrancarReloj(): void {
    this.detenerReloj();
    this.calcularSegundos();
    this.reloj = setInterval(() => this.calcularSegundos(), 1000);
  }

  private detenerReloj(): void {
    if (this.reloj) clearInterval(this.reloj);
    this.reloj = null;
  }

  private calcularSegundos(): void {
    const expira = this.expira();
    if (!expira) {
      this.segundos.set(0);
      return;
    }
    const faltan = Math.max(0, Math.floor((expira.getTime() - Date.now()) / 1000));
    this.segundos.set(faltan);
    if (faltan === 0) {
      this.detenerReloj();
      this.mesa.set(null);
      this.expira.set(null);
      this.error.set('Se acabó el tiempo para apartar la mesa. Elígela otra vez.');
      void this.cargarCroquis();
    }
  }

  get tiempoApartado(): string {
    const s = this.segundos();
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }

  async irAConfirmar(): Promise<void> {
    if (this.mesa() === null) return;
    this.paso.set(3);
    if (!this.carta().length) {
      try {
        this.carta.set(await this.api.carta());
      } catch { /* la carta no es indispensable para reservar */ }
    }
  }

  // =====================================================================
  // Paso 3: pedido y confirmación
  // =====================================================================

  cantidadDe(idPlatillo: number): number {
    return this.elegidos().find((l) => l.idPlatillo === idPlatillo)?.cantidad ?? 0;
  }

  notasDe(idPlatillo: number): string {
    return this.elegidos().find((l) => l.idPlatillo === idPlatillo)?.notas ?? '';
  }

  sumar(platillo: { id: number; nombre: string; precio: number }, cuantos: number): void {
    this.elegidos.update((lista) => {
      const copia = [...lista];
      const i = copia.findIndex((l) => l.idPlatillo === platillo.id);
      if (i === -1) {
        if (cuantos > 0) {
          copia.push({
            idPlatillo: platillo.id, nombre: platillo.nombre,
            precio: platillo.precio, cantidad: cuantos, notas: '',
          });
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

  /**
   * Guarda la reserva. Si la persona no tiene cuenta hay que resolver eso primero:
   * si está entrando, entra; si se está registrando, se le manda un código al correo
   * y la reserva se guarda hasta que lo escriba. Mientras tanto su mesa sigue apartada.
   */
  async confirmar(): Promise<void> {
    this.error.set('');

    // Registro paso 1: todavía no hay cuenta ni reserva, solo se pide el código
    if (!this.usuario() && this.modoCuenta() === 'registro' && this.pasoCuenta() === 'datos') {
      await this.pedirCodigo();
      return;
    }

    this.guardando.set(true);
    try {
      if (!this.usuario()) {
        if (this.modoCuenta() === 'registro') {
          // Registro paso 2: el código correcto crea la cuenta y abre la sesión
          await this.api.confirmarRegistro(this.solicitudId()!, this.codigoCorreo().trim());
          this.detenerRelojCodigo();
        } else {
          await this.api.entrar(this.correo().trim(), this.contrasena());
        }
      }

      const datos = {
        fecha: this.fecha(),
        hora: this.hora(),
        personas: this.personas(),
        idMesa: this.mesa(),
        ocasion: this.ocasion().trim() || null,
        notas: this.notas().trim() || null,
        lineas: this.conPedido()
          ? this.elegidos().map((l) => ({
              idPlatillo: l.idPlatillo, cantidad: l.cantidad, notas: l.notas.trim() || null,
            }))
          : undefined,
      };

      const anterior = this.editando();
      const reserva = anterior
        ? await this.api.cambiarReserva(anterior.id, datos)
        : await this.api.reservar(datos);

      this.detenerReloj();
      this.lista.set(reserva);
      this.paso.set(4);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.guardando.set(false);
    }
  }

  /** Manda el código de verificación al correo sin soltar la mesa apartada. */
  async pedirCodigo(): Promise<void> {
    this.guardando.set(true);
    try {
      const r = await this.api.solicitarRegistro({
        nombre: this.nombre().trim(),
        telefono: this.telefono().trim(),
        correo: this.correo().trim(),
        contrasena: this.contrasena(),
      });
      this.solicitudId.set(r.solicitudId);
      // El aviso se guarda solo para los reenvios: el primer mensaje ya lo dice la pantalla
      this.avisoCuenta.set('');
      if (r.codigo) this.codigoCorreo.set(r.codigo);   // modo de pruebas sin correo
      this.pasoCuenta.set('codigo');
      this.arrancarRelojCodigo();
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.guardando.set(false);
    }
  }

  async reenviarCodigo(): Promise<void> {
    const id = this.solicitudId();
    if (id === null || this.segundosParaReenviar() > 0) return;
    this.error.set('');
    try {
      const r = await this.api.reenviarCodigo(id);
      this.avisoCuenta.set(r.mensaje);
      if (r.codigo) this.codigoCorreo.set(r.codigo);
      this.arrancarRelojCodigo();
    } catch (e) {
      this.error.set(mensajeDeError(e));
    }
  }

  /** Regresar a corregir el correo sin perder la mesa apartada. */
  corregirDatosDeCuenta(): void {
    this.pasoCuenta.set('datos');
    this.codigoCorreo.set('');
    this.avisoCuenta.set('');
    this.detenerRelojCodigo();
  }

  private arrancarRelojCodigo(): void {
    this.detenerRelojCodigo();
    this.segundosParaReenviar.set(60);
    this.relojCodigo = setInterval(() => {
      this.segundosParaReenviar.update((s) => Math.max(0, s - 1));
      if (this.segundosParaReenviar() === 0) this.detenerRelojCodigo();
    }, 1000);
  }

  private detenerRelojCodigo(): void {
    if (this.relojCodigo) clearInterval(this.relojCodigo);
    this.relojCodigo = null;
  }

  /** Cuando no hay mesas: dejar el teléfono para que el restaurante avise. */
  async anotarmeEnEspera(): Promise<void> {
    this.error.set('');
    try {
      const r = await this.api.anotarmeEnEspera({
        fecha: this.fecha(),
        hora: this.hora() || (this.restaurante()?.franjas[0] ?? '20:00'),
        personas: this.personas(),
      });
      this.avisoEspera.set(r.mensaje);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    }
  }

  volverA(paso: number): void {
    this.paso.set(paso);
  }

  cambiarModoCuenta(modo: 'registro' | 'entrar'): void {
    this.modoCuenta.set(modo);
    this.pasoCuenta.set('datos');
    this.avisoCuenta.set('');
    this.error.set('');
    this.detenerRelojCodigo();
  }

  async verMisReservas(): Promise<void> {
    await this.router.navigate(['/mis-reservas']);
  }

  get listoParaConfirmar(): boolean {
    if (this.guardando()) return false;
    if (this.mesa() === null) return false;
    if (this.usuario()) return true;
    if (this.modoCuenta() === 'entrar') {
      return this.correo().trim().length > 3 && this.contrasena().length >= 1;
    }
    if (this.pasoCuenta() === 'codigo') return this.codigoCorreo().trim().length === 6;
    return this.nombre().trim().length >= 3
      && this.telefono().trim().length === 10
      && this.correo().trim().length > 3
      && this.contrasena().length >= 8;
  }

  /** Lo que dice el botón grande, que va cambiando según el paso. */
  get textoDelBoton(): string {
    if (this.guardando()) return 'Un momento…';
    if (!this.usuario() && this.modoCuenta() === 'registro') {
      if (this.pasoCuenta() === 'datos') return 'Mandarme el código y reservar';
      return 'Verificar y confirmar mi reserva';
    }
    return this.editando() ? 'Guardar los cambios' : 'Confirmar mi reserva';
  }
}
