# Datos simulados del MOD-06

Datos simulados (mock) que usa el back-end cuando `DATA_SOURCE=mock` en `backend/.env`. El front-end nunca lee estos archivos: siempre consulta la API.

## Reglas de formato

- **Un archivo por entidad**, con el nombre **exacto** de la entidad del Modelo de BD v1 (PascalCase, sin tildes) y extensión `.json`. Cada archivo es un **arreglo JSON**. Por ahora todos contienen `[]`: los datos se cargarán después.
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
- Entidades de negocio: incluyen además `fecha_creacion`, `usuario_creacion_id`, `fecha_modificacion` y `usuario_modificacion_id`. Los catálogos (TipoServicio, ClaseActividad, TipoActividad, UnidadMedida, Plaga, ProductoAgronomico, SectorMantenimiento, DefinicionIndicador) y las tablas de asociación (ParticipacionActividad) no los llevan.
- Ningún dato personal real. `Usuario.credencial_hash` siempre vacío en los mocks.
- Los informes de proveedor de prueba (PDF) van en `archivos/`.

## Entidades y atributos

### mod01

- **Usuario:** id, nombre_usuario, correo, credencial_hash, rol_id, personal_id, activo, ultimo_acceso

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
