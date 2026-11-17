import { Component, ElementRef, computed, input, output, signal, viewChild } from '@angular/core';
import { MesaCroquis, Zona } from '../../api/modelos';

/** Cuántos píxeles del dibujo mide un metro del restaurante. */
const ESCALA = 42;
/** Margen alrededor del plano, en metros. */
const MARGEN = 0.6;

/**
 * El croquis del restaurante: dibuja las zonas y las mesas en su lugar real
 * y deja que la persona elija dónde quiere sentarse.
 *
 * Se usa en tres pantallas distintas:
 *   - Reservar: el cliente toca una mesa libre (modo = 'elegir').
 *   - Piso:     el personal ve quién está en cada mesa (modo = 'piso').
 *   - Administración: se arrastran las mesas para acomodar el plano (modo = 'acomodar').
 */
@Component({
  selector: 'app-croquis',
  templateUrl: './croquis.html',
  styleUrl: './croquis.css',
})
export class Croquis {
  readonly zonas = input<Zona[]>([]);
  readonly mesas = input<MesaCroquis[]>([]);
  /** Mesa marcada en este momento. */
  readonly seleccionada = input<number | null>(null);
  /** Cuántas personas van: las mesas más chicas se ven apagadas. */
  readonly personas = input<number>(0);
  readonly modo = input<'elegir' | 'piso' | 'acomodar'>('elegir');
  readonly conLeyenda = input<boolean>(true);

  readonly elegida = output<MesaCroquis>();
  readonly movida = output<{ id: number; x: number; y: number }>();

  private readonly svg = viewChild<ElementRef<SVGSVGElement>>('dibujo');
  /** Mesa que se está arrastrando en el modo acomodar. */
  private arrastrando: number | null = null;
  readonly enMovimiento = signal<number | null>(null);

  // ---------------- Medidas del dibujo ----------------

  readonly ancho = computed(() => {
    const zonas = this.zonas();
    const porZonas = Math.max(0, ...zonas.map((z) => z.x + z.ancho));
    const porMesas = Math.max(0, ...this.mesas().map((m) => m.x + 1.5));
    return (Math.max(porZonas, porMesas, 8) + MARGEN * 2) * ESCALA;
  });

  readonly alto = computed(() => {
    const zonas = this.zonas();
    const porZonas = Math.max(0, ...zonas.map((z) => z.y + z.alto));
    const porMesas = Math.max(0, ...this.mesas().map((m) => m.y + 1.5));
    return (Math.max(porZonas, porMesas, 8) + MARGEN * 2) * ESCALA;
  });

  readonly cajaDeVista = computed(() => `0 0 ${this.ancho()} ${this.alto()}`);

  /** Metros -> píxeles del dibujo, contando el margen (para posiciones). */
  px(metros: number): number {
    return (metros + MARGEN) * ESCALA;
  }

  /** Metros -> píxeles del dibujo, sin margen (para anchos y altos). */
  escalar(metros: number): number {
    return metros * ESCALA;
  }

  /** Radio de la mesa en píxeles: crece con la capacidad. */
  radio(mesa: MesaCroquis): number {
    return (0.45 + mesa.capacidad * 0.06) * ESCALA;
  }

  // ---------------- Estado de cada mesa ----------------

  /** Mesa que no alcanza para el grupo: se puede ver pero no elegir. */
  chica(mesa: MesaCroquis): boolean {
    return this.personas() > 0 && mesa.capacidad < this.personas();
  }

  disponible(mesa: MesaCroquis): boolean {
    if (this.modo() === 'acomodar') return true;
    if (this.chica(mesa)) return false;
    if (this.modo() === 'piso') return true;
    return mesa.estado === 'LIBRE' || mesa.estado === 'PROPIA';
  }

  clase(mesa: MesaCroquis): string {
    const partes = ['mesa', 'e-' + mesa.estado.toLowerCase()];
    if (mesa.id === this.seleccionada()) partes.push('marcada');
    if (this.chica(mesa)) partes.push('chica');
    if (this.disponible(mesa)) partes.push('se-puede');
    if (this.enMovimiento() === mesa.id) partes.push('moviendo');
    return partes.join(' ');
  }

  /** Texto que aparece al dejar el puntero encima. */
  ayuda(mesa: MesaCroquis): string {
    const partes = [`Mesa ${mesa.id} · ${mesa.capacidad} personas`];
    if (mesa.zona) partes.push(mesa.zona);
    if (mesa.etiquetas) partes.push(mesa.etiquetas.split(',').join(', '));
    if (mesa.estado === 'OCUPADA') partes.push(mesa.ocupadaPor ? `Ocupada: ${mesa.ocupadaPor}` : 'Ocupada');
    if (mesa.estado === 'APARTADA') partes.push('Alguien la está apartando');
    if (mesa.estado === 'FUERA_DE_SERVICIO') partes.push('Fuera de servicio');
    if (this.chica(mesa)) partes.push(`No alcanza para ${this.personas()} personas`);
    return partes.join(' · ');
  }

  tocar(mesa: MesaCroquis): void {
    if (this.modo() === 'acomodar') return;
    if (!this.disponible(mesa)) return;
    if (this.modo() === 'piso' && mesa.estado === 'FUERA_DE_SERVICIO') return;
    this.elegida.emit(mesa);
  }

  /** Para poder elegir mesa con el teclado (Enter o barra espaciadora). */
  tecla(evento: KeyboardEvent, mesa: MesaCroquis): void {
    if (evento.key === 'Enter' || evento.key === ' ') {
      evento.preventDefault();
      this.tocar(mesa);
    }
  }

  // ---------------- Arrastrar mesas (solo administración) ----------------

  empezarArrastre(evento: PointerEvent, mesa: MesaCroquis): void {
    if (this.modo() !== 'acomodar') return;
    evento.preventDefault();
    this.arrastrando = mesa.id;
    this.enMovimiento.set(mesa.id);
    (evento.target as Element).setPointerCapture?.(evento.pointerId);
  }

  moverArrastre(evento: PointerEvent): void {
    if (this.arrastrando === null) return;
    const punto = this.enMetros(evento);
    if (!punto) return;
    const mesa = this.mesas().find((m) => m.id === this.arrastrando);
    if (!mesa) return;
    // Se redondea a 10 cm para que las mesas queden alineadas
    const x = Math.max(0, Math.round(punto.x * 10) / 10);
    const y = Math.max(0, Math.round(punto.y * 10) / 10);
    mesa.x = x;
    mesa.y = y;
    this.movida.emit({ id: mesa.id, x, y });
  }

  terminarArrastre(): void {
    this.arrastrando = null;
    this.enMovimiento.set(null);
  }

  /** Convierte la posición del puntero en la pantalla a metros del restaurante. */
  private enMetros(evento: PointerEvent): { x: number; y: number } | null {
    const elemento = this.svg()?.nativeElement;
    if (!elemento) return null;
    const matriz = elemento.getScreenCTM();
    if (!matriz) return null;
    const punto = elemento.createSVGPoint();
    punto.x = evento.clientX;
    punto.y = evento.clientY;
    const dentro = punto.matrixTransform(matriz.inverse());
    return { x: dentro.x / ESCALA - MARGEN, y: dentro.y / ESCALA - MARGEN };
  }
}
