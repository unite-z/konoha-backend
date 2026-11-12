# MOD-03: configuración de los servicios periódicos

Contrato y reglas de RF-PLA-001 (frecuencia por estación) y RF-PLA-002 (duración máxima)
en el backend, y cómo se prueban. Revisado contra `main` el 12/11/2026 (migraciones hasta
`V80`).

## Qué no cubre

MOD-03 «Planificación» tiene más partes que esta guía no documenta: restricciones del
calendario, programación y reprogramación de ciclos, ciclo de corte de césped (ruta, marcado,
limpieza, interrupciones y traslados), incidencias (registro, clasificación, inspección,
derivación a MOD-05, plazos y cierre), alertas y avisos. Su contrato está en OpenAPI
(`/api/docs`, etiqueta «MOD-03 Planificación»), en los controladores de
`src/main/java/pe/edu/pucp/areasverdes/mod03/` y en las pruebas de
`src/test/java/pe/edu/pucp/areasverdes/mod03/`. Las tablas, en `docs/base-datos/README.md`
(dos páginas del modelo ER para MOD-03).

Tampoco cubre el frontend (`konoha-frontend`, `src/pages/planificacion/`) ni la validación en
UAT o con la Sección.

## Datos

| Tabla | Migración | Qué guarda |
|---|---|---|
| `mod03_frecuencia_servicio` | `V2`, `V43` | Una fila por servicio y estación: teórica (obligatoria), práctica y máxima (opcionales), en días |
| `mod03_duracion_servicio` | `V2`, `V43` | Una fila por servicio: duración máxima (obligatoria), días de limpieza y horario opcional |
| `mod03_valor_inicial_frecuencia`, `mod03_valor_inicial_duracion` | `V40`, `V44` | Valores iniciales de la sección para «Usar valores iniciales» (AV-363), cargados en todos los entornos |
| `core_registro_auditoria` | `V7` (MOD-01) | Cada cambio de configuración, con la entidad `mod03_configuracion_servicio` y el id del servicio como registro |

- El servicio es `mod05_tiposervicio` (deuda de `V1`): `V2` le añade `es_periodico` y
  `es_ruidoso`. Solo se configuran los servicios activos y periódicos.
- La base impone lo mismo que la api: valores positivos, `dias_limpieza >= 0`, una fila por
  servicio y estación, `hora_fin > hora_inicio` y máxima `>= coalesce(práctica, teórica)`.
- Autor: `usuario_creacion_id` y `usuario_modificacion_id` apuntan a `core_usuario` desde
  `V43` (AV-287), no a `mod05_usuario`. Un autor sin cuenta de MOD-01 viola la clave ajena y
  la api responde 409 `Conflicto` (manejador global de `mod05/ErroresControllerAdvice`).
- Auditoría: `ConfiguracionServicios` la escribe con `RegistradorAuditoria` de MOD-01 en la
  misma transacción. La primera configuración del servicio es un `Alta`; las siguientes,
  `Modificacion` con los valores anteriores y nuevos como texto (y la estación, si es una
  frecuencia). La hora sale del `Clock` de la aplicación. `mod05_registroauditoria` ya no se usa
  para esto.

## Endpoints

Bajo `/api/v1/planificacion/`, con o sin barra final.

| Método y ruta | Permiso | Qué hace |
|---|---|---|
| `GET servicios/` | `PLA.PLANIFICACION.CONSULTAR` | Servicios activos y periódicos con `via`, `proveedor`, sus `frecuencias` y su `duracion` |
| `PUT servicios/{id}/frecuencias/{estacion}/` | `PLA.PLANIFICACION.GESTIONAR` | Guarda la frecuencia de una estación; devuelve el servicio completo |
| `PUT servicios/{id}/duracion/` | `PLA.PLANIFICACION.GESTIONAR` | Guarda la duración; devuelve el servicio completo |
| `GET servicios/{id}/valores-iniciales/` | `PLA.PLANIFICACION.CONSULTAR` | Valores iniciales de la sección; 404 `sin-valores-iniciales` si no hay |
| `GET servicios/{id}/historial/` | `PLA.PLANIFICACION.CONSULTAR` | Cambios de la configuración, del más reciente al más antiguo: `campo`, `estacion`, `anterior`, `nuevo`, `fecha_hora` y `autor`; la primera configuración es una sola fila `inicial` |
| `GET servicios/{id}/propuesta/` | `PLA.PLANIFICACION.CONSULTAR` | Fechas del siguiente ciclo con la frecuencia de la estación actual y la duración |

`estacion` es `Verano`, `Otoño`, `Invierno` o `Primavera` (en la URL, `Oto%C3%B1o`).

Los PUT reemplazan la configuración entera: un campo opcional que no se envía queda en `null`.

```json
PUT /api/v1/planificacion/servicios/1/frecuencias/Verano/
{"frecuencia_teorica_dias": 21, "frecuencia_practica_dias": 30, "frecuencia_maxima_dias": 45}

PUT /api/v1/planificacion/servicios/1/duracion/
{"duracion_maxima_dias": 10, "dias_limpieza": 1, "hora_inicio": "06:00", "hora_fin": "12:00"}
```

Cada frecuencia y la duración devuelven además `fecha_modificacion` (en Lima) y
`modificado_por` (el nombre de `core_usuario`).

### Permisos y sesión

- La aplicación entra con la sesión de MOD-01 (cookie y CSRF). HTTP Basic con los usuarios de
  `mod05_usuario` queda para pruebas: sus permisos son los del rol de MOD-01 equivalente.
- Sin credenciales: 401 `no-autenticado`. Sin el permiso: 403 `sin-permiso`.
- Por defecto (`V9`), consultan jefe de sección, ingeniero de campo, practicante autorizado y
  capataz, y solo el jefe configura. Desde AV-381 (`V75`) la matriz `core_rol_permiso` se puede
  editar desde la aplicación, así que lo que manda es el código de permiso, no el rol.

### Errores

Formato `{codigo, detalle}`, más `errores` por campo en las validaciones
(`mod03/ErrorPlanificacion`).

| Estado | `codigo` | Cuándo |
|---|---|---|
| 400 | `validacion` | Teórica o duración ausentes, `null`, cero o negativas; práctica o máxima no positivas; limpieza negativa |
| 400 | `frecuencia-maxima-menor` | La máxima es menor que la práctica o, si no hay práctica, que la teórica |
| 400 | `estacion-invalida` | La estación no es una de las cuatro |
| 400 | `horario-invalido` | `hora_fin` no es posterior a `hora_inicio` |
| 404 | `servicio-no-encontrado` | El servicio no existe o está inactivo |
| 409 | `servicio-no-periodico` | El servicio existe, pero no es periódico |

Una petición rechazada no cambia la fila ni deja auditoría.

## Quién usa la configuración

- Propuesta del siguiente ciclo (`ServiciosController.propuesta`): inicio = fin del último
  ciclo `Completada` + frecuencia práctica (o teórica) de la estación de hoy, nunca antes de
  hoy; fin = inicio + duración máxima + días de limpieza − 1. Sin frecuencia de la estación o
  sin duración: 409 `configuracion-incompleta`.
- Alta y reprogramación de ciclos (`ProgramacionServicios`): un ciclo no puede durar, contando
  ambos días, más que la duración máxima más la limpieza (RN-002): 400 `duracion-excedida`.
  Sin duración: 409 `configuracion-incompleta`.
- Alertas (`AlertasPlanificacion`): `FrecuenciaProxima` usa la frecuencia máxima de la
  estación; `DuracionProxima` y `DuracionExcedida`, la duración más la limpieza.
- MOD-06: el reporte de cumplimiento del corte (`CumplimientoCorteService`, con
  `DATA_SOURCE=db`, el valor por defecto) lee ambas tablas con `FuenteDatosBD`; la priorización
  y los cronogramas de IA leen la frecuencia práctica.
- La estación sale de la fecha en Lima (`Programador.estacion`): otoño desde el 21/3, invierno
  desde el 21/6, primavera desde el 23/9 y verano desde el 21/12.

## Pruebas

Todas levantan Spring sobre PostGIS (ver `docs/estandares/pruebas.md`), salvo `ProgramadorTest`
y `AccesoTest`, que son unitarias.

| Prueba | Qué cubre |
|---|---|
| `ConfigurarFrecuenciaApiTest`, `ConfigurarDuracionApiTest` | Casos básicos del contrato y auditoría con valor anterior |
| `ConfiguracionServiciosApiTest` | Verificación de RF-PLA-001 y RF-PLA-002: cuatro estaciones independientes; rechazos que no cambian la fila ni la auditoría; permisos de escritura (401/403) sin cambios; autor, instante y valores de cada cambio con dos jefes distintos, también por `historial`; lectura por los tres roles; servicio inactivo, no periódico o inexistente; la propuesta y el alta de ciclos consumen lo guardado por HTTP |
| `FrecuenciaDuracionIntegrationTest` | Restricciones de la base de `V2` |
| `ServiciosApiTest`, `HistorialServicioApiTest`, `PropuestaApiTest`, `AltaProgramacionApiTest` | Listado, historial, propuesta y RN-002 |
| `MatrizPermisosApiTest`, `SeguridadApiTest`, `CuentaSoloMod01ApiTest`, `AccesoTest` | Permisos por endpoint, sesión y cuentas solo de MOD-01 |

`ConfiguracionServiciosApiTest` fija el reloj en el 28/10/2026 (primavera) y crea su propio
servicio, sin depender de los ids del catálogo. El segundo jefe que usa se crea como los de
`ApiPlanificacion`: en `mod05_usuario` (para HTTP Basic) y en `core_usuario` con el mismo id.

```sh
./mvnw test -Dtest='ConfiguracionServiciosApiTest'
```
