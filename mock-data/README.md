# Datos simulados del MOD-06

Datos simulados (mock) que usa el back-end cuando `DATA_SOURCE=mock` en `backend/.env`. El front-end nunca lee estos archivos: siempre consulta la API.

Datos de ejemplo coherentes con los documentos del equipo; no son datos reales de operación.

## Reglas de formato

- **Un archivo por entidad**, con el nombre **exacto** de la entidad del Modelo de BD v1 (PascalCase, sin tildes) y extensión `.json`. Cada archivo es un **arreglo JSON**.
- La subcarpeta indica el **módulo propietario** de la entidad según el Modelo de BD v1 (tabla 2).
- Atributos en `snake_case`, **exactamente** como en el diccionario de datos.
- Tipos:
  - Identificador → entero (el tipo físico está por decidir).
  - Fecha → `"AAAA-MM-DD"`.
  - Fecha y hora → ISO 8601 con zona (`"2026-10-20T08:30:00-05:00"`).
  - Hora → `"HH:MM"`.
  - Decimal y Entero → número.
  - Booleano → `true` / `false`.
  - JSON → objeto.
  - Geometría → objeto GeoJSON con el orden **longitud, latitud** en SRID 4326 (por ejemplo `{"type": "Point", "coordinates": [-77.0796, -12.0672]}`).
- Valores de enumerados: el texto exacto de la tabla 6 del Modelo de BD v1 (por ejemplo `"En corte"`, `"Conforme / cerrado"`, `"PersonalPropio"`).
- Entidades de negocio: incluyen además `fecha_creacion`, `usuario_creacion_id`, `fecha_modificacion` y `usuario_modificacion_id`. Los catálogos (Rol, TipoServicio, ClaseActividad, TipoActividad, UnidadMedida, Plaga, ProductoAgronomico, SectorMantenimiento, DefinicionIndicador) y las tablas de asociación (ParticipacionActividad) no los llevan.
- Ningún dato personal real. `Usuario.credencial_hash` siempre vacío en los mocks.
- Los informes de proveedor de prueba (PDF) van en `archivos/`.
- `SugerenciaIA.json` y `ReporteGenerado.json` son las únicas entidades que escribe el back-end del MOD-06. `SugerenciaIA.json` empieza en `[]`; para volver al estado inicial, deja el archivo otra vez en `[]`.

## Notas sobre los datos de ejemplo

- **Entidades que no se cargan en este corte** (sus ids aparecen como claves foráneas sin archivo): TipoUso, TipoCobertura, Especie, UnidadInstitucional, Incidencia, Encargo, CampanaPoda y TipoIntervencion. Esas claves van en `null`, salvo:
  - `AreaVerde.tipo_uso_id`: valores 1 a 4 (1 recreativo, 2 institucional, 3 ornamental, 4 deportivo);
  - `Ejemplar.especie_id`: 1 (*Ceiba speciosa*) y 2 (*Roystonea regia*).
- **Plaga** y **ProductoAgronomico** quedan en `[]`: los documentos no dan nombres de ejemplo.
- **TipoServicio:** `es_periodico`, `es_ruidoso` y `es_riesgo` toman los valores que dan los documentos (Corte de césped periódico y ruidoso; Control fitosanitario y Poda mayor programada periódicos; Poda de altura a demanda no periódica y de riesgo). Donde el documento no indica nada, el valor es `false`.
- **Contrato:** datos económicos en `null` (RI-34); `modalidad: "Tarifario"` solo como valor de ejemplo.
- **Ciclo 8** (`"Programado"`, sin `fecha_inicio_real`): su programación tiene `fecha_inicio_prevista` 2026-11-09, fuera del rango 01/01–20/10/2026, porque un ciclo programado y no iniciado es, por definición, futuro.
- **Apoyo eventual (excepción intencional a RI-21):** el Operario 10 (Cuadrilla 2) participa el 14/10/2026 en una actividad de la Cuadrilla 1 (CU-MOD04-011 1a).
- **RF-IA-001:** el PDF del informe no se incluye (no hay informe real de proveedor; pendiente con la contraparte). Coloca en `mock-data/archivos/` un informe de proveedor en PDF con el nombre `informe-fitosanitario-ejemplo.pdf` para probar RF-IA-001 de forma manual.

## Escenarios de prueba

- **RF-REP-002 (CP-TR-MOD06-009):** ciclos 1 (sin ciclo anterior), 2 (28 días, dentro), 3 (38 días, tolerado), 4 (53 días, fuera de frecuencia), 5 (4 días de corte y 2 de limpieza, excede el máximo), 6 (Primavera, límite no configurado), 7 (`"En corte"`, sin fecha de fin de corte) y 8 (`"Programado"`, no evaluable). No hay FrecuenciaServicio de Primavera a propósito.
- **RF-REP-004:** partes del 13 al 17/10/2026; la Cuadrilla 3 no tiene parte el 15/10 («Sin parte aún»); el Operario 03 participa en dos actividades el 13/10; la actividad de la Cuadrilla 2 del 17/10 no tiene `tiempo_minutos`.
- **RF-REP-006 (CP-TR-MOD06-014):** AT-2026-031 (poda de altura en Bosque Húmedo, sin sector) con alcance por área y por ejemplar que apuntan a la misma área (cuenta 1); AT-2026-032 en `"OC referenciada"` (no cuenta); aplicación fitosanitaria 1 vinculada a AT-2026-033 (no se duplica) y aplicación 2 sin atención (cuenta por sí misma); poda menor con actividad (no se duplica), poda de altura con atención (no se duplica) y poda menor sobre el ejemplar EJ-00733 sin área («Sin espacio»).
- **RF-IA-001 (CP-TR-MOD06-010):** ReporteProveedor 1 (control fitosanitario, formato vigente, PDF), 2 (corte de césped, sin formato vigente → 422) y 3 (sin informe adjunto → 404).

## Entidades y atributos

### mod01

- **Usuario:** id, nombre_usuario, correo, credencial_hash, rol_id, personal_id, activo, ultimo_acceso
- **Rol:** id, nombre, descripcion, activo (Jefe de sección, Ingeniero de campo, Practicante, Capataz y Operario; este último con `activo: false`, «previsto, sin acceso en la primera fase»)

### mod02

- **Lugar:** id, nombre, referencia, ubicacion
- **SectorMantenimiento:** id, nombre, descripcion, atencion_a_demanda, activo
- **AsignacionSector:** id, sector_id, capataz_id, fecha_inicio, fecha_fin, motivo
- **Cuartel:** id, numero, nombre, estado (Vigente | Desaparecido), geometria
- **AreaVerde:** id, nombre, lugar_id, sector_id, tipo_uso_id, tipo_cobertura_id, cuartel_id, superficie_m2, geometria, unidad_responsable_id, zona_alta_actividad, activo
- **RutaCorte:** id, nombre, descripcion, vigente
- **Ejemplar:** id, codigo, codigo_placa_antigua, especie_id, origen, area_verde_id, lugar_id, cuartel_id, ubicacion, altura_total_m, altura_fuste_m, dap_cm, radio_copa_m, estado_salud, estado_sanitario, situacion, ejemplar_reemplazado_id, fecha_registro
- **IntervencionArbolado:** id, ejemplar_id, categoria (Menor | MayorCampana | AlturaDemanda), campana_id, actividad_id, atencion_id, incidencia_id, tipo_actividad_id, tipo_intervencion_id, motivo, altura_referencia_m, fecha_ejecucion, reduccion_copa_pct, ramas_laterales_cortadas, cantidad_solicitada, cantidad_ejecutada, unidad_medida_id, prioridad, requiere_validacion

### mod03

- **TipoServicio:** id, nombre, ejecutor (PersonalPropio | Tercerizado), es_periodico, es_ruidoso, es_riesgo, activo
- **FrecuenciaServicio:** id, tipo_servicio_id, estacion (Verano | Otoño | Invierno | Primavera), frecuencia_teorica_dias, frecuencia_practica_dias, frecuencia_maxima_dias
- **DuracionServicio:** id, tipo_servicio_id, duracion_maxima_dias, dias_limpieza, hora_inicio, hora_fin
- **ProgramacionServicio:** id, tipo_servicio_id, proveedor_id, cuadrilla_id, estacion, fecha_inicio_prevista, fecha_fin_prevista, ventana_mes, estado (Programada | EnCurso | Completada | Cancelada), creado_por_id
- **CicloCorte:** id, programacion_id, ruta_id, proveedor_id, atencion_id, estado (Programado | En corte | Interrumpido | Corte completado | En limpieza | Cerrado), fecha_inicio_real, fecha_fin_corte, fecha_inicio_limpieza, fecha_fin_limpieza, ultima_actualizacion

### mod04

- **Personal:** id, nombres, apellidos, documento_identidad, telefono, tipo_personal (JefeSeccion | IngenieroCampo | Practicante | Capataz | Operario | Prevencionista | Otro), activo
- **Cuadrilla:** id, nombre, capataz_id, activa
- **AsignacionCuadrilla:** id, operario_id, cuadrilla_id, fecha_inicio, fecha_fin, motivo_cambio
- **ClaseActividad:** id, nombre, descripcion, activo
- **TipoActividad:** id, clase_id, nombre, descripcion, activo
- **UnidadMedida:** id, simbolo, nombre
- **ParteDiario:** id, fecha, cuadrilla_id, capataz_id, estado (Abierto | En registro | Cerrado), hora_apertura, hora_cierre, resumen_cierre
- **Actividad:** id, parte_id, tipo_actividad_id, area_verde_id, ejemplar_id, fecha_hora_actividad, fecha_solicitud, incidencia_id, encargo_id, superficie_m2, cantidad_solicitada, cantidad_ejecutada, unidad_medida_id, tiempo_minutos, observaciones, es_rutina, fecha_hora_sincronizacion
- **ParticipacionActividad:** actividad_id, operario_id

### mod05

- **Proveedor:** id, razon_social, ruc, proveedor_principal_id, contacto, telefono, correo, activo
- **Contrato:** id, numero, proveedor_id, tipo_servicio_id, metrado_estimado, unidad_medida_id, modalidad (MontoFijo | Tarifario), monto_fijo, moneda, frecuencia_pactada_dias, vigencia_inicio, vigencia_fin, condiciones
- **AtencionTercerizada:** id, codigo, contrato_id, tipo_servicio_id, clase (ProgramadaContrato | Extraordinaria), origen (Demanda | ProgramacionEstacional | SolicitudUnidad), unidad_solicitante_id, acuerdo_unidad, incidencia_id, programacion_id, numero_orden_compra, numero_orden_servicio, fecha_hora_inicio, fecha_hora_fin, estado (Requerimiento registrado | OC referenciada | En ejecución | Ejecutado pendiente de reporte | Reporte adjunto | Observado | Conforme / cerrado), es_riesgo, solicitado_por_id
- **AlcanceAtencion:** id, atencion_id, area_verde_id, ejemplar_id, metrado, unidad_medida_id
- **ReporteProveedor:** id, atencion_id, revision, fecha_recepcion, metrado_reportado, unidad_medida_id, resumen
- **FormatoReporteServicio:** id, tipo_servicio_id, version, vigente
- **CampoFormatoReporte:** id, formato_id, nombre_campo, tipo_dato, obligatorio
- **Plaga:** id, nombre, nombre_cientifico, tipo_organismo (Insecto | Hongo | Otro), activo
- **ProductoAgronomico:** id, nombre, categoria, unidad_dosis, activo
- **AplicacionFitosanitaria:** id, programacion_id, atencion_id, tipo (Estacional | AdicionalAtencionEspecial | PorObservacion), estacion, fecha, hora_inicio, hora_fin, observaciones
- **AplicacionObjetivo:** id, aplicacion_id, area_verde_id, ejemplar_id

### mod06

- **DefinicionIndicador:** id, codigo, nombre, descripcion_calculo, meta, unidad, activo
- **ReporteGenerado:** id, tipo (DistribucionPersonal | MaterialVegetal | ServicioTercerizado | GestionOSG | MatrizIncidencias | Listado), parametros, formato (PDF | Excel), usuario_id, fecha_hora, archivo_id
- **SugerenciaIA:** id, tipo (ExtraccionReporte | Inconsistencia | Priorizacion), entidad, registro_id, archivo_id, contenido, estado (Propuesta | Validada | Rechazada), validador_id, fecha_validacion

### transversal

- **Archivo:** id, nombre_original, tipo_mime, tamano_bytes, ruta_almacenamiento, hash_contenido, comprimido, fecha_captura
- **ArchivoAsociado:** id, archivo_id, entidad, registro_id, categoria (Fotografia | EvidenciaCierre | ReporteProveedor | FichaDiaria | CharlaTecnica | ListadoPlantas | ListadoPlagas | ListadoProductos | RutaRecorrida | Documento), descripcion

## Entidades por requisito

- **RF-REP-002:** TipoServicio, FrecuenciaServicio, DuracionServicio, ProgramacionServicio, CicloCorte, RutaCorte, Proveedor.
- **RF-REP-004:** ParteDiario, Actividad, ParticipacionActividad, Personal, Cuadrilla, AsignacionCuadrilla, ClaseActividad, TipoActividad, UnidadMedida, AreaVerde, Lugar.
- **RF-REP-006:** AreaVerde, SectorMantenimiento, AsignacionSector, Cuartel, Actividad, AtencionTercerizada, AlcanceAtencion, AplicacionFitosanitaria, AplicacionObjetivo, IntervencionArbolado, Ejemplar.
- **RF-IA-001:** AtencionTercerizada, Contrato, TipoServicio, ReporteProveedor, FormatoReporteServicio, CampoFormatoReporte, Archivo, ArchivoAsociado, SugerenciaIA, Plaga, ProductoAgronomico, UnidadMedida, Usuario. Los PDF de prueba irán en `archivos/`.
