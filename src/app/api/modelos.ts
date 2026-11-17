/** Las formas de los datos que viajan entre Angular y Spring. */

export interface Usuario {
  id: number;
  idPersona: number;
  nombre: string;
  correo: string;
  telefono: string | null;
  rol: 'CLIENTE' | 'EMPLEADO' | 'ADMINISTRADOR';
  rolNombre: string;
  esAdmin: boolean;
  esPersonal: boolean;
  debeCambiarContrasena: boolean;
  /** Token de sesión que da Spring al entrar. Se manda en cada petición (token.interceptor). */
  token?: string;
}

export interface Restaurante {
  nombre: string;
  frase: string;
  descripcion: string;
  direccion: string;
  telefono: string;
  fotos: string;
  horaApertura: string;
  horaCierre: string;
  duracionMin: number;
  pasoMin: number;
  aforo: number;
  toleranciaMin: number;
  minutosApartado: number;
  diasParaReservar: number;
  horasParaCambiar: number;
  franjas: string[];
  /** true cuando el servidor trae las cuentas y reservas de ejemplo. */
  demo?: boolean;
}

export interface Zona {
  id: number;
  nombre: string;
  x: number;
  y: number;
  ancho: number;
  alto: number;
  color: string;
  tipo: 'SALA' | 'SERVICIO';
  descripcion: string | null;
  reservable: boolean;
}

export type EstadoMesa = 'LIBRE' | 'OCUPADA' | 'APARTADA' | 'PROPIA' | 'FUERA_DE_SERVICIO';

export interface MesaCroquis {
  id: number;
  capacidad: number;
  x: number;
  y: number;
  forma: 'REDONDA' | 'CUADRADA';
  etiquetas: string | null;
  zona: string | null;
  idZona: number | null;
  estado: EstadoMesa;
  /** Solo lo ve el personal. */
  ocupadaPor?: string | null;
  idReserva?: number | null;
}

export interface Mesa {
  id: number;
  capacidad: number;
  activa: boolean;
  zona: { id: number; nombre?: string } | null;
  x: number;
  y: number;
  forma: 'REDONDA' | 'CUADRADA';
  etiquetas: string | null;
}

export interface Dia {
  fecha: string;
  estado: 'PASADO' | 'BLOQUEADO' | 'LEJANO' | 'OCUPADO' | 'PARCIAL' | 'LIBRE';
}

export interface Horario {
  hora: string;
  fin: string;
  mesasLibres: number;
  disponible: boolean;
  motivo: string | null;
}

export interface Platillo {
  id: number;
  nombre: string;
  descripcion: string | null;
  precio: number;
  imagen: string | null;
  activo: boolean;
  etiquetas: string | null;
  alergenos: string | null;
  categoria?: { id: string; nombre: string };
}

export interface GrupoCarta {
  id: string;
  nombre: string;
  platillos: Platillo[];
}

export type EstadoReserva = 'CONFIRMADA' | 'EN_MESA' | 'FINALIZADA' | 'CANCELADA' | 'NO_SE_PRESENTO';

export interface Reserva {
  id: number;
  fecha: string;
  hora: string;
  horaFin: string;
  personas: number;
  estado: EstadoReserva;
  estadoNombre: string;
  codigo: string | null;
  ocasion: string | null;
  notas: string | null;
  idMesa: number;
  capacidadMesa: number;
  zona: string | null;
  cliente: string;
  registradaPorEmpleado: boolean;
  horaLlegada: string | null;
  horaSalida: string | null;
  pasada: boolean;
  puedeCambiar: boolean;
  tarde: boolean;
  telefono?: string | null;
  correo?: string | null;
}

export interface LineaPedido {
  id: number;
  idPlatillo: number;
  platillo: string;
  categoria: string;
  cantidad: number;
  precioUnitario: number;
  importe: number;
  notas: string | null;
}

export interface Pedido {
  id: number;
  idReserva: number;
  idMesa: number;
  cliente: string;
  estado: 'PENDIENTE' | 'PREPARANDO' | 'SERVIDO' | 'CANCELADO';
  estadoNombre: string;
  fecha: string;
  total: number;
  lineas: LineaPedido[];
}

export interface CuentaDeMesa {
  idReserva: number;
  pedidos: Pedido[];
  total: number;
}

/** Lo que se manda para crear o cambiar una reserva. */
export interface ReservaPeticion {
  fecha: string;
  hora: string;
  personas: number;
  idMesa: number | null;
  ocasion?: string | null;
  notas?: string | null;
  nombre?: string;
  telefono?: string;
  correo?: string;
  lineas?: { idPlatillo: number; cantidad: number; notas?: string | null }[];
}

export interface Parametro {
  id: string;
  valor: string;
  descripcion: string | null;
}

export interface UsuarioAdmin extends Usuario {
  activo: boolean;
  fechaRegistro: string;
  reservas: number;
}

export interface EnEspera {
  id: number;
  fecha: string;
  hora: string;
  personas: number;
  cliente: string;
  telefono: string | null;
  correo: string | null;
  estado: string;
}

export interface Reportes {
  desde: string;
  hasta: string;
  reservas: number;
  comensales: number;
  canceladas: number;
  noSePresentaron: number;
  porcentajeNoShow: number;
  ventaEnPedidos: number;
  porEstado: Record<string, number>;
  porDia: Record<string, number>;
  porHora: Record<string, number>;
  porZona: Record<string, number>;
  platillosMasPedidos: { platillo: string; cantidad: number }[];
  mesasActivas: number;
  platillosActivos: number;
}
