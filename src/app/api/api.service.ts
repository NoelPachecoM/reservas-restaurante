import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_URL } from './api-config';
import {
  CuentaDeMesa, Dia, EnEspera, GrupoCarta, Horario, Mesa, MesaCroquis, Parametro, Pedido,
  Platillo, Reportes, Reserva, ReservaPeticion, Restaurante, Usuario, UsuarioAdmin, Zona,
} from './modelos';

/** Respuesta de un paso de un trámite de datos personales (baja o cambio de correo). */
export interface RespuestaArco {
  solicitudId: number;
  mensaje: string;
  minutos?: number;
  /** ACTUAL = falta el código del correo de hoy; NUEVO = falta el del correo nuevo. */
  paso?: 'CODIGO' | 'ACTUAL' | 'NUEVO';
  correoNuevo?: string;
  /** Solo aparece si el servidor está en modo de pruebas sin correo. */
  codigo?: string;
}

/** Respuesta de los dos primeros pasos del registro. */
export interface RespuestaRegistro {
  solicitudId: number;
  correo?: string;
  mensaje: string;
  /** Solo aparece si el servidor está en modo de pruebas sin correo. */
  codigo?: string;
}

const CLAVE_SESION = 'reservas_restaurante_usuario';
const CLAVE_VISITA = 'reservas_restaurante_visita';

/** Convierte cualquier error de HTTP en un texto para mostrar en pantalla. */
export function mensajeDeError(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) {
      return 'No se pudo conectar con el servidor. Revisa que Spring esté iniciado.';
    }
    const cuerpo = e.error;
    if (cuerpo && typeof cuerpo === 'object' && typeof cuerpo.error === 'string') return cuerpo.error;
    if (typeof cuerpo === 'string' && cuerpo.trim()) return cuerpo;
    return `Error ${e.status}`;
  }
  if (e instanceof Error && e.message) return e.message;
  return 'Ocurrió un error inesperado.';
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly _usuario = signal<Usuario | null>(this.leerSesion());

  readonly usuario = this._usuario.asReadonly();
  readonly haIniciadoSesion = computed(() => this._usuario() !== null);
  readonly esPersonal = computed(() => this._usuario()?.esPersonal === true);
  readonly esAdmin = computed(() => this._usuario()?.esAdmin === true);

  /**
   * Clave con la que este navegador aparta mesas. Permite que una persona sin cuenta
   * aparte una mesa y que el servidor sepa que ese apartado es suyo.
   */
  readonly clave = this.leerClaveDeVisita();

  // =====================================================================
  // Sesión
  // =====================================================================

  async entrar(usuario: string, contrasena: string): Promise<Usuario> {
    const datos = await this.post<Usuario>('/api/auth/login', { usuario, contrasena });
    this.guardarSesion(datos);
    return datos;
  }

  /**
   * Paso 1 del registro: Spring manda un código de 6 dígitos al correo.
   * La cuenta todavía NO se crea: primero hay que comprobar que el correo existe.
   */
  solicitarRegistro(datos: {
    nombre: string; telefono: string; correo: string; contrasena: string; codigoEmpleado?: string;
  }, comoEmpleado = false): Promise<RespuestaRegistro> {
    const ruta = comoEmpleado ? '/api/auth/registro/solicitar-empleado' : '/api/auth/registro/solicitar';
    return this.post<RespuestaRegistro>(ruta, datos);
  }

  /** Vuelve a mandar el código (Spring pide esperar un minuto entre envíos). */
  reenviarCodigo(solicitudId: number): Promise<RespuestaRegistro> {
    return this.post<RespuestaRegistro>('/api/auth/registro/reenviar', { solicitudId });
  }

  /** Paso 2: con el código correcto se crea la cuenta y se inicia sesión. */
  async confirmarRegistro(solicitudId: number, codigo: string): Promise<Usuario> {
    const usuario = await this.post<Usuario>('/api/auth/registro/confirmar', { solicitudId, codigo });
    this.guardarSesion(usuario);
    return usuario;
  }

  token(): string | null {
    return this._usuario()?.token ?? null;
  }

  /** Vuelve a leer de Spring los datos del usuario (sobre todo el rol), por si cambiaron. */
  async refrescarSesion(): Promise<Usuario | null> {
    const actual = this._usuario();
    if (!actual?.token) {
      if (actual) this.olvidarSesion();
      return null;
    }
    try {
      const usuario = await this.get<Usuario>('/api/auth/yo');
      const completo = { ...usuario, token: actual.token };
      this.guardarSesion(completo);
      return completo;
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 401) {
        this.olvidarSesion();
        return null;
      }
      return actual;
    }
  }

  salir(): void {
    if (this.token()) {
      this.http.post(this.url('/api/auth/salir'), {}).subscribe({ error: () => undefined });
    }
    this.olvidarSesion();
  }

  olvidarSesion(): void {
    try {
      localStorage.removeItem(CLAVE_SESION);
    } catch { /* sin almacenamiento */ }
    this._usuario.set(null);
  }

  // =====================================================================
  // Mi cuenta
  // =====================================================================

  async cambiarMiCuenta(cambios: {
    contrasenaActual: string; nombre?: string; telefono?: string; correo?: string; contrasenaNueva?: string;
  }): Promise<Usuario> {
    const datos = await this.put<Usuario>('/api/auth/mi-cuenta', cambios);
    const actual = this._usuario();
    if (actual) this.guardarSesion({ ...actual, ...datos, token: actual.token });
    return datos;
  }

  // ---- Derechos ARCO: los trámites que no se pueden deshacer ----
  // Los dos piden la contraseña Y un código que llega al correo.

  /** Cancelación, paso 1: manda el código al correo de la cuenta. */
  pedirCodigoDeBaja(contrasenaActual: string): Promise<RespuestaArco> {
    return this.post<RespuestaArco>('/api/auth/mi-cuenta/eliminar/solicitar', { contrasenaActual });
  }

  /** Cancelación, paso 2: con la contraseña y el código, la cuenta se borra. */
  async confirmarBaja(solicitudId: number, codigo: string, contrasenaActual: string) {
    const r = await this.post<{ mensaje: string }>('/api/auth/mi-cuenta/eliminar/confirmar',
      { solicitudId, codigo, contrasenaActual });
    this.olvidarSesion();
    return r;
  }

  /** Cambio de correo, paso 1: código al correo de ahora. */
  pedirCambioDeCorreo(contrasenaActual: string, correoNuevo: string): Promise<RespuestaArco> {
    return this.post<RespuestaArco>('/api/auth/mi-cuenta/correo/solicitar',
      { contrasenaActual, correoNuevo });
  }

  /** Cambio de correo, paso 2: se confirma ese código y sale otro al correo nuevo. */
  confirmarCorreoActual(solicitudId: number, codigo: string): Promise<RespuestaArco> {
    return this.post<RespuestaArco>('/api/auth/mi-cuenta/correo/confirmar-actual',
      { solicitudId, codigo });
  }

  /** Cambio de correo, paso 3: con el código del correo nuevo se aplica el cambio. */
  async confirmarCorreoNuevo(solicitudId: number, codigo: string): Promise<Usuario> {
    const datos = await this.post<Usuario>('/api/auth/mi-cuenta/correo/confirmar-nuevo',
      { solicitudId, codigo });
    const actual = this._usuario();
    if (actual) this.guardarSesion({ ...actual, ...datos, token: actual.token });
    return datos;
  }

  /** Reenvía el código que toque del trámite abierto. */
  reenviarCodigoDeCuenta(solicitudId: number): Promise<RespuestaArco> {
    return this.post<RespuestaArco>('/api/auth/mi-cuenta/reenviar', { solicitudId });
  }

  cancelarTramite(): Promise<{ mensaje: string }> {
    return this.post<{ mensaje: string }>('/api/auth/mi-cuenta/cancelar-tramite', {});
  }

  pedirCodigo(correo: string) {
    return this.post<{ mensaje: string }>('/api/auth/olvide/codigo', { correo });
  }

  cambiarConCodigo(correo: string, codigo: string, contrasenaNueva: string) {
    return this.post<{ mensaje: string }>('/api/auth/olvide/cambiar', { correo, codigo, contrasenaNueva });
  }

  // =====================================================================
  // Público
  // =====================================================================

  restaurante() {
    return this.get<Restaurante>('/api/publico/restaurante');
  }

  carta() {
    return this.get<GrupoCarta[]>('/api/publico/carta');
  }

  zonas() {
    return this.get<Zona[]>('/api/publico/zonas');
  }

  hoy() {
    return this.get<{ fecha: string }>('/api/publico/hoy');
  }

  dias(anio: number, mes: number, personas: number) {
    return this.get<Dia[]>('/api/publico/dias', { anio, mes, personas });
  }

  horarios(fecha: string, personas: number) {
    return this.get<Horario[]>('/api/publico/horarios', { fecha, personas, clave: this.clave });
  }

  croquis(fecha: string, hora: string) {
    return this.get<MesaCroquis[]>('/api/publico/croquis', { fecha, hora, clave: this.clave });
  }

  apartar(idMesa: number, fecha: string, hora: string) {
    return this.post<{ idMesa: number; expira: string; minutos: number }>(
      '/api/publico/apartar', {}, { idMesa, fecha, hora, clave: this.clave });
  }

  soltar() {
    return this.post<{ mensaje: string }>('/api/publico/soltar', {}, { clave: this.clave });
  }

  porCodigo(codigo: string) {
    return this.get<Reserva>(`/api/publico/reserva/${encodeURIComponent(codigo)}`);
  }

  // =====================================================================
  // Mis reservas
  // =====================================================================

  misReservas() {
    return this.get<Reserva[]>('/api/reservas');
  }

  reserva(id: number) {
    return this.get<Reserva>(`/api/reservas/${id}`);
  }

  reservar(datos: ReservaPeticion) {
    return this.post<Reserva>('/api/reservas', datos, { clave: this.clave });
  }

  cambiarReserva(id: number, datos: ReservaPeticion) {
    return this.put<Reserva>(`/api/reservas/${id}`, datos, { clave: this.clave });
  }

  cancelarReserva(id: number) {
    return this.post<Reserva>(`/api/reservas/${id}/cancelar`, {});
  }

  anotarmeEnEspera(datos: { fecha: string; hora: string; personas: number }) {
    return this.post<{ mensaje: string }>('/api/reservas/lista-espera', datos);
  }

  cuentaDeMiReserva(id: number) {
    return this.get<CuentaDeMesa>(`/api/reservas/${id}/pedidos`);
  }

  pedir(idReserva: number, lineas: { idPlatillo: number; cantidad: number; notas?: string | null }[]) {
    return this.post<Pedido>(`/api/reservas/${idReserva}/pedidos`, { lineas });
  }

  cambiarPedido(idPedido: number, lineas: { idPlatillo: number; cantidad: number; notas?: string | null }[]) {
    return this.put<Pedido>(`/api/reservas/pedidos/${idPedido}`, { lineas });
  }

  borrarPedido(idPedido: number) {
    return this.delete<{ mensaje: string }>(`/api/reservas/pedidos/${idPedido}`);
  }

  // =====================================================================
  // Piso (empleado y administrador)
  // =====================================================================

  agenda(fecha?: string) {
    return this.get<Reserva[]>('/api/piso/agenda', fecha ? { fecha } : {});
  }

  croquisDePiso(fecha?: string, hora?: string) {
    const params: Record<string, string> = {};
    if (fecha) params['fecha'] = fecha;
    if (hora) params['hora'] = hora;
    return this.get<MesaCroquis[]>('/api/piso/croquis', params);
  }

  cambiarEstadoReserva(id: number, valor: string) {
    return this.post<Reserva>(`/api/piso/reservas/${id}/estado`, {}, { valor });
  }

  moverDeMesa(id: number, idMesa: number) {
    return this.post<Reserva>(`/api/piso/reservas/${id}/mover`, {}, { idMesa });
  }

  reservarEnPiso(datos: ReservaPeticion) {
    return this.post<Reserva>('/api/piso/reservas', datos);
  }

  cuentaDeMesa(idReserva: number) {
    return this.get<CuentaDeMesa>(`/api/piso/reservas/${idReserva}/pedidos`);
  }

  pedirEnPiso(idReserva: number, lineas: { idPlatillo: number; cantidad: number; notas?: string | null }[]) {
    return this.post<Pedido>(`/api/piso/reservas/${idReserva}/pedidos`, { lineas });
  }

  cambiarEstadoPedido(idPedido: number, valor: string) {
    return this.post<Pedido>(`/api/piso/pedidos/${idPedido}/estado`, {}, { valor });
  }

  cocina() {
    return this.get<Pedido[]>('/api/piso/cocina');
  }

  buscarPorCodigo(codigo: string) {
    return this.get<Reserva>('/api/piso/buscar', { codigo });
  }

  // =====================================================================
  // Administración
  // =====================================================================

  mesas() {
    return this.get<Mesa[]>('/api/admin/mesas');
  }

  crearMesa(mesa: Partial<Mesa>) {
    return this.post<Mesa>('/api/admin/mesas', mesa);
  }

  cambiarMesa(id: number, mesa: Partial<Mesa>) {
    return this.put<Mesa>(`/api/admin/mesas/${id}`, mesa);
  }

  guardarPosiciones(mesas: { id: number; x: number; y: number; zona?: { id: number } | null }[]) {
    return this.put<Mesa[]>('/api/admin/mesas/posiciones', mesas);
  }

  borrarMesa(id: number) {
    return this.delete<{ mensaje: string }>(`/api/admin/mesas/${id}`);
  }

  parametros() {
    return this.get<Parametro[]>('/api/admin/parametros');
  }

  guardarParametros(valores: Record<string, string>) {
    return this.put<Restaurante>('/api/admin/parametros', valores);
  }

  codigoEmpleado() {
    return this.get<{ codigo: string }>('/api/admin/codigo-empleado');
  }

  nuevoCodigoEmpleado() {
    return this.post<{ codigo: string }>('/api/admin/codigo-empleado', {});
  }

  usuarios() {
    return this.get<UsuarioAdmin[]>('/api/admin/usuarios');
  }

  cambiarRol(id: number, valor: string) {
    return this.post<Usuario>(`/api/admin/usuarios/${id}/rol`, {}, { valor });
  }

  activarUsuario(id: number, valor: boolean) {
    return this.post<Usuario>(`/api/admin/usuarios/${id}/activo`, {}, { valor });
  }

  reiniciarContrasena(id: number) {
    return this.post<{ contrasenaTemporal: string; mensaje: string }>(
      `/api/admin/usuarios/${id}/contrasena`, {});
  }

  listaEspera() {
    return this.get<EnEspera[]>('/api/admin/lista-espera');
  }

  quitarDeEspera(id: number) {
    return this.delete<{ mensaje: string }>(`/api/admin/lista-espera/${id}`);
  }

  reportes(desde?: string, hasta?: string) {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.get<Reportes>('/api/admin/reportes', params);
  }

  todasLasReservas(desde?: string, hasta?: string) {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.get<Record<string, any>[]>('/api/admin/reservas', params);
  }

  // ---- Catálogos del administrador (CRUD igual para todos) ----

  listarCatalogo<T>(nombre: string) {
    return this.get<T[]>(`/api/admin/catalogos/${nombre}`);
  }

  crearEnCatalogo<T>(nombre: string, cuerpo: Record<string, unknown>) {
    return this.post<T>(`/api/admin/catalogos/${nombre}`, cuerpo);
  }

  cambiarEnCatalogo<T>(nombre: string, id: number, cuerpo: Record<string, unknown>) {
    return this.put<T>(`/api/admin/catalogos/${nombre}/${id}`, cuerpo);
  }

  borrarDeCatalogo(nombre: string, id: number) {
    return this.delete<void>(`/api/admin/catalogos/${nombre}/${id}`);
  }

  platillosTodos() {
    return this.listarCatalogo<Platillo>('platillos');
  }

  // =====================================================================
  // Privado
  // =====================================================================

  private url(ruta: string): string {
    return `${API_URL}${ruta}`;
  }

  private parametrosDe(valores?: Record<string, string | number | boolean>): HttpParams {
    let params = new HttpParams();
    if (valores) {
      for (const [clave, valor] of Object.entries(valores)) {
        if (valor !== undefined && valor !== null && valor !== '') params = params.set(clave, String(valor));
      }
    }
    return params;
  }

  private get<T>(ruta: string, valores?: Record<string, string | number | boolean>): Promise<T> {
    return firstValueFrom(this.http.get<T>(this.url(ruta), { params: this.parametrosDe(valores) }));
  }

  private post<T>(ruta: string, cuerpo: unknown, valores?: Record<string, string | number | boolean>): Promise<T> {
    return firstValueFrom(this.http.post<T>(this.url(ruta), cuerpo, { params: this.parametrosDe(valores) }));
  }

  private put<T>(ruta: string, cuerpo: unknown, valores?: Record<string, string | number | boolean>): Promise<T> {
    return firstValueFrom(this.http.put<T>(this.url(ruta), cuerpo, { params: this.parametrosDe(valores) }));
  }

  private delete<T>(ruta: string): Promise<T> {
    return firstValueFrom(this.http.delete<T>(this.url(ruta)));
  }

  private guardarSesion(usuario: Usuario): void {
    try {
      localStorage.setItem(CLAVE_SESION, JSON.stringify(usuario));
    } catch { /* sin almacenamiento */ }
    this._usuario.set(usuario);
  }

  private leerSesion(): Usuario | null {
    try {
      const texto = localStorage.getItem(CLAVE_SESION);
      return texto ? (JSON.parse(texto) as Usuario) : null;
    } catch {
      return null;
    }
  }

  /** Clave de este navegador para los apartados de mesa. Se crea una vez y se reutiliza. */
  private leerClaveDeVisita(): string {
    try {
      const guardada = localStorage.getItem(CLAVE_VISITA);
      if (guardada) return guardada;
      const nueva = 'v' + Math.random().toString(36).slice(2, 12) + Date.now().toString(36);
      localStorage.setItem(CLAVE_VISITA, nueva);
      return nueva;
    } catch {
      return 'v' + Math.random().toString(36).slice(2, 12);
    }
  }
}
