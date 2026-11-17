import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService, mensajeDeError } from '../../api/api.service';
import { Croquis } from '../../componentes/croquis/croquis';
import { CATALOGOS, Campo, Catalogo, PARAMETROS_VISIBLES } from './campos';
import { EnEspera, Mesa, MesaCroquis, Parametro, Reportes, UsuarioAdmin, Zona } from '../../api/modelos';

type Pestana = 'croquis' | 'catalogos' | 'reservas' | 'config' | 'usuarios' | 'reportes' | 'espera';

/** Campos que en la base son una llave a otra tabla y viajan como objeto {id: '...'}. */
const REFERENCIAS = new Set(['categoria']);

/**
 * Panel del administrador. Está dividido en pestañas: el croquis (acomodar las mesas),
 * los catálogos (carta, áreas, días cerrados), las reservas, la configuración del
 * restaurante, las cuentas, los números del negocio y la lista de espera.
 */
@Component({
  selector: 'app-administracion',
  imports: [RouterLink, Croquis],
  templateUrl: './administracion.html',
  styleUrl: './administracion.css',
})
export class Administracion {
  private readonly api = inject(ApiService);

  readonly pestana = signal<Pestana>('croquis');
  readonly error = signal('');
  readonly aviso = signal('');
  readonly cargando = signal(false);

  // ---------------- Croquis y mesas ----------------
  readonly zonas = signal<Zona[]>([]);
  readonly mesas = signal<Mesa[]>([]);
  readonly cambiadas = signal<Set<number>>(new Set());
  readonly mesaEnForma = signal<Partial<Mesa> | null>(null);
  readonly esMesaNueva = signal(false);

  /** Las mesas en el formato que entiende el croquis. */
  readonly mesasParaCroquis = computed<MesaCroquis[]>(() =>
    this.mesas().map((m) => ({
      id: m.id,
      capacidad: m.capacidad,
      x: m.x,
      y: m.y,
      forma: m.forma,
      etiquetas: m.etiquetas,
      zona: this.zonas().find((z) => z.id === m.zona?.id)?.nombre ?? null,
      idZona: m.zona?.id ?? null,
      estado: m.activa ? 'LIBRE' : 'FUERA_DE_SERVICIO',
    })));

  readonly zonasConMesas = computed(() => this.zonas().filter((z) => z.reservable));

  // ---------------- Catálogos ----------------
  readonly catalogos = CATALOGOS;
  readonly catalogo = signal<Catalogo>(CATALOGOS[0]);
  readonly filas = signal<Record<string, any>[]>([]);
  readonly enForma = signal<Record<string, any> | null>(null);
  readonly esNuevo = signal(false);

  // ---------------- Configuración ----------------
  readonly parametrosVisibles = PARAMETROS_VISIBLES;
  readonly parametros = signal<Parametro[]>([]);
  readonly valores = signal<Record<string, string>>({});
  readonly codigoEmpleado = signal('');

  // ---------------- Cuentas ----------------
  readonly usuarios = signal<UsuarioAdmin[]>([]);
  readonly temporal = signal<{ nombre: string; contrasena: string } | null>(null);

  // ---------------- Reservas y números ----------------
  readonly reservas = signal<Record<string, any>[]>([]);
  readonly desde = signal('');
  readonly hasta = signal('');
  readonly reportes = signal<Reportes | null>(null);
  readonly espera = signal<EnEspera[]>([]);

  readonly maximoPorDia = computed(() => {
    const porDia = this.reportes()?.porDia ?? {};
    return Math.max(1, ...Object.values(porDia));
  });

  readonly diasDelReporte = computed(() => {
    const porDia = this.reportes()?.porDia ?? {};
    return Object.entries(porDia).map(([fecha, cuantas]) => ({ fecha, cuantas }));
  });

  readonly horasDelReporte = computed(() => {
    const porHora = this.reportes()?.porHora ?? {};
    const maximo = Math.max(1, ...Object.values(porHora));
    return Object.entries(porHora).map(([hora, cuantas]) => ({ hora, cuantas, maximo }));
  });

  readonly zonasDelReporte = computed(() => {
    const porZona = this.reportes()?.porZona ?? {};
    const total = Object.values(porZona).reduce((s, v) => s + v, 0) || 1;
    return Object.entries(porZona).map(([zona, cuantas]) => ({
      zona, cuantas, porciento: Math.round((cuantas * 100) / total),
    }));
  });

  constructor() {
    void this.cargarCroquis();
  }

  async irA(pestana: Pestana): Promise<void> {
    this.pestana.set(pestana);
    this.error.set('');
    this.aviso.set('');
    this.enForma.set(null);
    this.mesaEnForma.set(null);
    switch (pestana) {
      case 'croquis': await this.cargarCroquis(); break;
      case 'catalogos': await this.cargarCatalogo(); break;
      case 'config': await this.cargarConfig(); break;
      case 'usuarios': await this.cargarUsuarios(); break;
      case 'reservas': await this.cargarReservas(); break;
      case 'reportes': await this.cargarReportes(); break;
      case 'espera': await this.cargarEspera(); break;
    }
  }

  private async con<T>(trabajo: () => Promise<T>): Promise<T | null> {
    this.cargando.set(true);
    this.error.set('');
    try {
      return await trabajo();
    } catch (e) {
      this.error.set(mensajeDeError(e));
      return null;
    } finally {
      this.cargando.set(false);
    }
  }

  // =====================================================================
  // Croquis y mesas
  // =====================================================================

  async cargarCroquis(): Promise<void> {
    await this.con(async () => {
      const [zonas, mesas] = await Promise.all([this.api.zonas(), this.api.mesas()]);
      this.zonas.set(zonas);
      this.mesas.set(mesas);
      this.cambiadas.set(new Set());
    });
  }

  /** El croquis avisa cada vez que se suelta una mesa en otro lugar. */
  anotarMovida(cambio: { id: number; x: number; y: number }): void {
    this.mesas.update((lista) =>
      lista.map((m) => (m.id === cambio.id ? { ...m, x: cambio.x, y: cambio.y } : m)));
    this.cambiadas.update((antes) => new Set(antes).add(cambio.id));
  }

  async guardarPosiciones(): Promise<void> {
    const ids = this.cambiadas();
    if (!ids.size) return;
    const cambios = this.mesas()
      .filter((m) => ids.has(m.id))
      .map((m) => ({ id: m.id, x: m.x, y: m.y }));
    const hecho = await this.con(() => this.api.guardarPosiciones(cambios));
    if (hecho) {
      this.cambiadas.set(new Set());
      this.aviso.set(`Se guardó la posición de ${cambios.length} mesas.`);
    }
  }

  nuevaMesa(): void {
    const siguiente = Math.max(0, ...this.mesas().map((m) => m.id)) + 1;
    this.esMesaNueva.set(true);
    this.mesaEnForma.set({
      id: siguiente, capacidad: 4, activa: true, forma: 'REDONDA',
      x: 1, y: 1, etiquetas: '', zona: this.zonasConMesas()[0] ? { id: this.zonasConMesas()[0].id } : null,
    });
  }

  editarMesa(mesa: Mesa): void {
    this.esMesaNueva.set(false);
    this.mesaEnForma.set({ ...mesa, zona: mesa.zona ? { id: mesa.zona.id } : null });
  }

  cambiarEnMesa(campo: string, valor: unknown): void {
    this.mesaEnForma.update((m) => {
      if (!m) return m;
      if (campo === 'zona') {
        const id = Number(valor);
        return { ...m, zona: id ? { id } : null };
      }
      return { ...m, [campo]: valor } as Partial<Mesa>;
    });
  }

  async guardarMesa(): Promise<void> {
    const mesa = this.mesaEnForma();
    if (!mesa) return;
    const hecho = await this.con(() =>
      this.esMesaNueva() ? this.api.crearMesa(mesa) : this.api.cambiarMesa(mesa.id!, mesa));
    if (hecho) {
      this.mesaEnForma.set(null);
      this.aviso.set(this.esMesaNueva() ? 'Mesa agregada.' : 'Mesa actualizada.');
      await this.cargarCroquis();
    }
  }

  async borrarMesa(mesa: Mesa): Promise<void> {
    if (!confirm(`¿Borrar la mesa ${mesa.id}? Si ya tiene reservas, mejor desactívala.`)) return;
    const hecho = await this.con(() => this.api.borrarMesa(mesa.id));
    if (hecho) {
      this.aviso.set('Mesa eliminada.');
      await this.cargarCroquis();
    }
  }

  nombreDeZona(mesa: Mesa): string {
    return this.zonas().find((z) => z.id === mesa.zona?.id)?.nombre ?? '—';
  }

  // =====================================================================
  // Catálogos
  // =====================================================================

  async elegirCatalogo(catalogo: Catalogo): Promise<void> {
    this.catalogo.set(catalogo);
    this.enForma.set(null);
    await this.cargarCatalogo();
  }

  async cargarCatalogo(): Promise<void> {
    const filas = await this.con(() =>
      this.api.listarCatalogo<Record<string, any>>(this.catalogo().ruta));
    if (filas) this.filas.set(filas);
  }

  get camposDeTabla(): Campo[] {
    return this.catalogo().campos.filter((c) => !c.soloEnFormulario);
  }

  /** Valor de un campo para mostrarlo en la tabla o en el formulario. */
  leer(fila: Record<string, any>, campo: Campo): any {
    const valor = fila[campo.nombre];
    if (REFERENCIAS.has(campo.nombre)) return valor?.id ?? '';
    return valor ?? '';
  }

  mostrar(fila: Record<string, any>, campo: Campo): string {
    const valor = fila[campo.nombre];
    if (REFERENCIAS.has(campo.nombre)) return valor?.nombre ?? '—';
    if (campo.tipo === 'si-no') return valor ? 'Sí' : 'No';
    if (valor === null || valor === undefined || valor === '') return '—';
    return String(valor);
  }

  nuevo(): void {
    const vacio: Record<string, any> = {};
    for (const campo of this.catalogo().campos) {
      vacio[campo.nombre] = campo.defecto ?? (campo.tipo === 'si-no' ? false : campo.tipo === 'numero' ? 0 : '');
      if (REFERENCIAS.has(campo.nombre) && campo.opciones?.length) {
        vacio[campo.nombre] = { id: campo.opciones[0].valor };
      }
    }
    this.esNuevo.set(true);
    this.enForma.set(vacio);
  }

  editar(fila: Record<string, any>): void {
    this.esNuevo.set(false);
    this.enForma.set({ ...fila });
  }

  cambiarEnForma(campo: Campo, valor: unknown): void {
    this.enForma.update((fila) => {
      if (!fila) return fila;
      if (REFERENCIAS.has(campo.nombre)) return { ...fila, [campo.nombre]: { id: valor } };
      if (campo.tipo === 'numero') return { ...fila, [campo.nombre]: Number(valor) };
      return { ...fila, [campo.nombre]: valor };
    });
  }

  async guardar(): Promise<void> {
    const fila = this.enForma();
    if (!fila) return;
    const faltan = this.catalogo().campos
      .filter((c) => c.obligatorio)
      .filter((c) => {
        const valor = REFERENCIAS.has(c.nombre) ? fila[c.nombre]?.id : fila[c.nombre];
        return valor === null || valor === undefined || valor === '';
      });
    if (faltan.length) {
      this.error.set(`Falta llenar: ${faltan.map((c) => c.titulo).join(', ')}`);
      return;
    }
    const ruta = this.catalogo().ruta;
    const hecho = await this.con(() =>
      this.esNuevo()
        ? this.api.crearEnCatalogo(ruta, fila)
        : this.api.cambiarEnCatalogo(ruta, fila['id'], fila));
    if (hecho) {
      this.enForma.set(null);
      this.aviso.set('Guardado.');
      await this.cargarCatalogo();
      // Si se cambiaron las áreas, el croquis también cambia
      if (ruta === 'zonas') this.zonas.set(await this.api.zonas());
    }
  }

  async borrar(fila: Record<string, any>): Promise<void> {
    if (!confirm(`¿Borrar este ${this.catalogo().singular}?`)) return;
    const hecho = await this.con(() => this.api.borrarDeCatalogo(this.catalogo().ruta, fila['id']));
    if (hecho !== null) {
      this.aviso.set('Eliminado.');
      await this.cargarCatalogo();
    }
  }

  // =====================================================================
  // Configuración
  // =====================================================================

  async cargarConfig(): Promise<void> {
    await this.con(async () => {
      const [parametros, codigo] = await Promise.all([this.api.parametros(), this.api.codigoEmpleado()]);
      this.parametros.set(parametros);
      this.codigoEmpleado.set(codigo.codigo);
      const valores: Record<string, string> = {};
      for (const p of parametros) valores[p.id] = p.valor;
      this.valores.set(valores);
    });
  }

  valorDe(id: string): string {
    return this.valores()[id] ?? '';
  }

  cambiarValor(id: string, valor: string): void {
    this.valores.update((antes) => ({ ...antes, [id]: valor }));
  }

  async guardarConfig(): Promise<void> {
    const hecho = await this.con(() => this.api.guardarParametros(this.valores()));
    if (hecho) this.aviso.set('Configuración guardada. Los cambios se ven de inmediato.');
  }

  async renovarCodigo(): Promise<void> {
    if (!confirm('El código anterior deja de servir. ¿Generar uno nuevo?')) return;
    const nuevo = await this.con(() => this.api.nuevoCodigoEmpleado());
    if (nuevo) {
      this.codigoEmpleado.set(nuevo.codigo);
      this.aviso.set('Código nuevo listo. Dáselo solo a tu personal.');
    }
  }

  // =====================================================================
  // Cuentas
  // =====================================================================

  async cargarUsuarios(): Promise<void> {
    const lista = await this.con(() => this.api.usuarios());
    if (lista) this.usuarios.set(lista);
  }

  async cambiarRol(u: UsuarioAdmin, valor: string): Promise<void> {
    const hecho = await this.con(() => this.api.cambiarRol(u.id, valor));
    if (hecho) {
      this.aviso.set(`${u.nombre} ahora es ${valor.toLowerCase()}.`);
      await this.cargarUsuarios();
    }
  }

  async activar(u: UsuarioAdmin, valor: boolean): Promise<void> {
    const hecho = await this.con(() => this.api.activarUsuario(u.id, valor));
    if (hecho) {
      this.aviso.set(valor ? 'Cuenta activada.' : 'Cuenta desactivada.');
      await this.cargarUsuarios();
    }
  }

  async reiniciar(u: UsuarioAdmin): Promise<void> {
    if (!confirm(`¿Poner una contraseña temporal a ${u.nombre}?`)) return;
    const r = await this.con(() => this.api.reiniciarContrasena(u.id));
    if (r) this.temporal.set({ nombre: u.nombre, contrasena: r.contrasenaTemporal });
  }

  // =====================================================================
  // Reservas, números y lista de espera
  // =====================================================================

  async cargarReservas(): Promise<void> {
    const lista = await this.con(() => this.api.todasLasReservas(this.desde(), this.hasta()));
    if (lista) this.reservas.set(lista);
  }

  async cargarReportes(): Promise<void> {
    const r = await this.con(() => this.api.reportes(this.desde(), this.hasta()));
    if (r) this.reportes.set(r);
  }

  async cargarEspera(): Promise<void> {
    const lista = await this.con(() => this.api.listaEspera());
    if (lista) this.espera.set(lista);
  }

  async quitarDeEspera(id: number): Promise<void> {
    const hecho = await this.con(() => this.api.quitarDeEspera(id));
    if (hecho) await this.cargarEspera();
  }

  alto(cuantas: number, maximo: number): string {
    return `${Math.max(4, Math.round((cuantas * 100) / maximo))}%`;
  }
}
