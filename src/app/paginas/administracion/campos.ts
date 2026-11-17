/**
 * Definición de los catálogos que administra el restaurante.
 *
 * Cada catálogo dice cómo se llama, a qué ruta de la API pega y qué campos tiene.
 * Con eso la pantalla de administración arma sola la tabla y el formulario, así que
 * para agregar un catálogo nuevo basta con escribirlo aquí.
 */

export interface Campo {
  nombre: string;
  titulo: string;
  tipo: 'texto' | 'parrafo' | 'numero' | 'si-no' | 'lista' | 'fecha' | 'color';
  /** Opciones cuando el tipo es 'lista'. */
  opciones?: { valor: string | number; texto: string }[];
  /** Si el valor es obligatorio para guardar. */
  obligatorio?: boolean;
  ayuda?: string;
  /** No se muestra en la tabla (solo en el formulario). */
  soloEnFormulario?: boolean;
  defecto?: unknown;
}

export interface Catalogo {
  clave: string;
  /** Ruta dentro de /api/admin/catalogos/. */
  ruta: string;
  titulo: string;
  singular: string;
  descripcion: string;
  campos: Campo[];
}

export const CATALOGO_PLATILLOS: Catalogo = {
  clave: 'platillos',
  ruta: 'platillos',
  titulo: 'La carta',
  singular: 'platillo',
  descripcion:
    'Lo que se puede pedir. Un platillo que se acabó se desactiva: así deja de aparecer en la '
    + 'carta pero no se pierde el historial de lo que ya se vendió.',
  campos: [
    { nombre: 'nombre', titulo: 'Nombre', tipo: 'texto', obligatorio: true },
    {
      nombre: 'categoria', titulo: 'Categoría', tipo: 'lista', obligatorio: true,
      opciones: [
        { valor: 'ENTRADAS', texto: 'Entradas' },
        { valor: 'PLATOS_FUERTES', texto: 'Platos fuertes' },
        { valor: 'POSTRES', texto: 'Postres' },
        { valor: 'BEBIDAS', texto: 'Bebidas' },
      ],
    },
    { nombre: 'precio', titulo: 'Precio', tipo: 'numero', obligatorio: true, defecto: 0 },
    { nombre: 'activo', titulo: 'Se vende', tipo: 'si-no', defecto: true },
    {
      nombre: 'descripcion', titulo: 'Descripción', tipo: 'parrafo', soloEnFormulario: true,
      ayuda: 'Lo que el cliente lee en la carta.',
    },
    {
      nombre: 'etiquetas', titulo: 'Etiquetas', tipo: 'texto', soloEnFormulario: true,
      ayuda: 'Separadas por comas: vegetariano, picante, sin gluten…',
    },
    {
      nombre: 'alergenos', titulo: 'Alérgenos', tipo: 'texto', soloEnFormulario: true,
      ayuda: 'Separados por comas: lacteos, gluten, nueces… Es lo que se le avisa al cliente.',
    },
    {
      nombre: 'imagen', titulo: 'Foto', tipo: 'texto', soloEnFormulario: true,
      ayuda: 'Dirección de la imagen (opcional).',
    },
  ],
};

export const CATALOGO_ZONAS: Catalogo = {
  clave: 'zonas',
  ruta: 'zonas',
  titulo: 'Áreas del restaurante',
  singular: 'área',
  descripcion:
    'Las áreas son los rectángulos del croquis y se miden en metros, como el local real. '
    + 'Las de tipo Servicio (cocina, baños, entrada) se dibujan pero no reciben mesas.',
  campos: [
    { nombre: 'nombre', titulo: 'Nombre', tipo: 'texto', obligatorio: true },
    {
      nombre: 'tipo', titulo: 'Tipo', tipo: 'lista', defecto: 'SALA',
      opciones: [
        { valor: 'SALA', texto: 'Sala (lleva mesas)' },
        { valor: 'SERVICIO', texto: 'Servicio (no se reserva)' },
      ],
    },
    { nombre: 'x', titulo: 'X (m)', tipo: 'numero', defecto: 0, ayuda: 'Esquina de arriba a la izquierda.' },
    { nombre: 'y', titulo: 'Y (m)', tipo: 'numero', defecto: 0 },
    { nombre: 'ancho', titulo: 'Ancho (m)', tipo: 'numero', defecto: 6 },
    { nombre: 'alto', titulo: 'Alto (m)', tipo: 'numero', defecto: 6 },
    { nombre: 'reservable', titulo: 'Se reserva', tipo: 'si-no', defecto: true },
    { nombre: 'color', titulo: 'Color', tipo: 'color', defecto: '#F7EFE1', soloEnFormulario: true },
    { nombre: 'descripcion', titulo: 'Descripción', tipo: 'parrafo', soloEnFormulario: true,
      ayuda: 'El cliente la lee en la portada.' },
  ],
};

export const CATALOGO_FECHAS: Catalogo = {
  clave: 'fechas-bloqueadas',
  ruta: 'fechas-bloqueadas',
  titulo: 'Días cerrados',
  singular: 'día cerrado',
  descripcion:
    'Días en los que no se reciben reservas: descansos, días festivos o un evento privado. '
    + 'El calendario del cliente los marca como cerrados.',
  campos: [
    { nombre: 'fecha', titulo: 'Fecha', tipo: 'fecha', obligatorio: true },
    { nombre: 'motivo', titulo: 'Motivo', tipo: 'texto', ayuda: 'Para acordarse después.' },
  ],
};

export const CATALOGOS: Catalogo[] = [CATALOGO_PLATILLOS, CATALOGO_ZONAS, CATALOGO_FECHAS];

/** Los parámetros que vale la pena editar desde la pantalla, con un nombre entendible. */
export const PARAMETROS_VISIBLES: { id: string; titulo: string; ayuda: string; tipo: 'texto' | 'numero' | 'hora' | 'parrafo' }[] = [
  { id: 'nombre_restaurante', titulo: 'Nombre del restaurante', ayuda: 'Aparece en la barra de arriba y en los correos.', tipo: 'texto' },
  { id: 'frase_restaurante', titulo: 'Frase de la portada', ayuda: 'Una línea corta.', tipo: 'texto' },
  { id: 'descripcion_restaurante', titulo: 'Presentación', ayuda: 'El párrafo de la portada.', tipo: 'parrafo' },
  { id: 'direccion_restaurante', titulo: 'Dirección', ayuda: '', tipo: 'texto' },
  { id: 'telefono_restaurante', titulo: 'Teléfono', ayuda: 'Para cancelaciones de último momento.', tipo: 'texto' },
  { id: 'fotos_restaurante', titulo: 'Fotos', ayuda: 'Direcciones de las imágenes, separadas por comas.', tipo: 'texto' },
  { id: 'hora_apertura', titulo: 'Primera reserva', ayuda: 'Formato 24 horas, por ejemplo 13:00.', tipo: 'hora' },
  { id: 'hora_cierre', titulo: 'Cierre', ayuda: 'La última reserva debe alcanzar a terminar antes de esta hora.', tipo: 'hora' },
  { id: 'duracion_reserva_min', titulo: 'Duración de la reserva (min)', ayuda: 'Cuánto ocupa la mesa cada reserva.', tipo: 'numero' },
  { id: 'minutos_entre_horarios', titulo: 'Minutos entre horarios', ayuda: '30 da 13:00, 13:30, 14:00…', tipo: 'numero' },
  { id: 'aforo_por_franja', titulo: 'Aforo máximo', ayuda: 'Comensales al mismo tiempo en todo el restaurante.', tipo: 'numero' },
  { id: 'tolerancia_min', titulo: 'Tolerancia (min)', ayuda: 'Pasados estos minutos, la reserva se marca con retraso.', tipo: 'numero' },
  { id: 'minutos_apartado', titulo: 'Minutos para apartar', ayuda: 'Cuánto se le guarda la mesa a quien la está eligiendo.', tipo: 'numero' },
  { id: 'dias_para_reservar', titulo: 'Días de anticipación', ayuda: 'Con cuánto tiempo se puede reservar.', tipo: 'numero' },
  { id: 'horas_para_cambiar', titulo: 'Horas para cambiar', ayuda: 'Hasta cuántas horas antes el cliente puede cambiar o cancelar.', tipo: 'numero' },
];
