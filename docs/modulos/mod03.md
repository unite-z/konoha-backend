# MOD-03: verificación de RF-PLA-001 y RF-PLA-002

Fecha: 28/10/2026, America/Lima. Auditoría local de backend `99c34fa` y frontend
`e2019e5`, con pruebas adicionales en la rama `chore/AV-7-verificacion-frecuencia-duracion`
de ambos repositorios. AV-7 es la referencia de la migración V2; no se ha consultado ni
modificado Linear. Infraestructura: solo lectura, sin cambios.

Este documento reúne los dos informes y los contratos de MOD-03 porque no existía una
guía del módulo en `docs/modulos/`. Los logs y scripts temporales quedan en `.notas/`,
ignorados por Git. No se han cambiado funcionalidades ni migraciones fusionadas.

## Alcance y estado inicial

- Se leyeron `AGENTS.md`, `README.md` y `CONTRIBUTING.md` de los tres repositorios,
  estándares de API, Java, base de datos, pruebas, React/TypeScript, nombres, Git y secretos.
- Los tres repositorios estaban en `main`. El backend tenía únicamente `.env` sin
  seguimiento; se preservó. Frontend e infra no tenían cambios locales.
- Antes de editar se crearon ramas separadas en backend y frontend. No hubo commits,
  push, fusiones, despliegues ni escrituras en Linear.
- Todas las pruebas SQL se ejecutaron en el contenedor **nuevo**
  `konoha-auditoria-pla-20261028`, base `auditoria_pla_test`, puerto local `55439`.
  Las suites heredadas usan `TRUNCATE ... CASCADE`; solo se les proporcionó esta base
  sintética. No se usó la base local existente de desarrollo ni una base UAT.
- El despliegue UAT del 25/10/2026 procede del contexto aportado por el responsable.
  No se han comprobado SHA desplegados, ejecuciones de CI ni resultados en UAT.

## Informe RF-PLA-001 — Configuración de frecuencias por estación

| Criterio | Clasificación | Evidencia y alcance |
|---|---|---|
| 1. Teórica, práctica y máxima por servicio/estación | Implementado | PUT y GET con PostgreSQL real; cuatro estaciones independientes, actualización sin duplicar. Práctica y máxima son opcionales según el contrato existente. |
| 2. Autor, fecha y hora de cada modificación | Implementado | Auditoría persistida de dos cambios con dos jefes: autor inicial/final, `fecha_hora`, valor anterior/nuevo y metadatos de creación/modificación. Instante del evento igual al del estado nuevo. |
| 3. Exponer para programación y reportes | Implementado en exposición y programación; pendiente en reportes reales | GET publica los valores. Al cambiar práctica de 30 a 40, la propuesta tras un ciclo terminado el 20/10 pasa del 19/11 al 29/11. MOD-06 calcula sobre mocks; `FuenteDatosBD` no está implementada. |
| 4. Rechazar máxima menor que habitual sin perder configuración | Implementado | HTTP 400 con práctica presente y ausente; comparación de toda la fila JSONB, incluidos metadatos, y recuento de auditorías antes/después. Ambos permanecen iguales. SQL V2 añade el mismo CHECK. |
| 5. Errores, permisos, persistencia e integración | Implementado localmente en API; pendiente de validación integrada final | Jefe escribe; ingeniero/practicante consultan y reciben 403 al escribir; sin rol 403; anónimo 401. Errores de dominio, tipos de servicio inválidos, consultas nuevas y transporte frontend cubiertos. Navegador y UAT sin verificar. |

**Resultado:** la configuración propia de MOD-03 está implementada y probada localmente.
No corresponde declarar cerrado el requisito integrado de reportes: un endpoint de
consulta disponible no demuestra que MOD-06 lea los valores persistidos.

### Archivos y métodos

Rutas relativas al backend salvo donde se indica:

- `mod03/ServiciosController.java`: `configurarFrecuencia`, `listar`, `propuesta`.
- `mod03/ConfiguracionServicios.java`: DTO `NuevaFrecuencia`, `frecuencia`,
  `exigirPeriodico`, `guardar`; transacción y auditoría en `mod05_registroauditoria`.
- `mod03/ServiciosRepository.java`: `periodicos`, `frecuencia`; lectura del autor y zona Lima.
- `mod03/ServicioPeriodico.java`: DTO de salida `Frecuencia`.
- `mod03/Acceso.java`, `SeguridadMod03.java`, `ErrorPlanificacion.java`: permisos,
  autenticación y respuestas de validación/dominio.
- Flyway: `V2__mod03_frecuencia_duracion_servicio.sql`; relación servicio/estación única,
  FK de autores, `timestamptz`, positivos y máxima >= `coalesce(práctica, teórica)`.
  V1 aporta usuarios y auditoría inmutable; V4 aporta la programación que consume valores.
- Frontend: `ServiciosPeriodicosPage.tsx`, `DialogoFrecuencia.tsx`,
  `services/api/planificacion.ts`, `clientePlanificacion.ts`,
  `types/planificacion.ts`, `utils/validacionPlanificacion.ts`,
  `config/permisosPlanificacion.ts`. El diálogo aplica el resultado solo al guardar
  correctamente y presenta los errores del servidor.
- Reportes: `mod06/servicios/CumplimientoCorteService.java` consume `FrecuenciaServicio`
  a través de `FuenteDatos`; `SelectorFuenteDatos` selecciona mocks o `FuenteDatosBD`.
  Esta última lanza `FuenteDatosNoDisponible`. La prueba existente
  `FuentesDatosTest.apiConBdResponde503` demuestra HTTP 503.

Las clases MOD-03 están bajo `src/main/java/pe/edu/pucp/areasverdes/mod03/`;
los diálogos bajo `konoha-frontend/src/pages/planificacion/`.

### Endpoint y ejemplos

`PUT /api/v1/planificacion/servicios/{id}/frecuencias/Oto%C3%B1o/`
(HTTP Basic, jefe). También acepta la ruta sin barra final.

```json
{"frecuencia_teorica_dias":21,"frecuencia_practica_dias":30,"frecuencia_maxima_dias":45}
```

HTTP 200: devuelve un `ServicioPeriodico` completo con `id`, `nombre`, `es_ruidoso`,
`frecuencias` y `duracion`. Fragmento ilustrativo de la frecuencia, con instante variable:

```json
{"estacion":"Otoño","frecuencia_teorica_dias":21,"frecuencia_practica_dias":30,"frecuencia_maxima_dias":45,"fecha_modificacion":"2026-10-28T10:00:00-05:00","modificado_por":"jefe"}
```

Con máxima 29 y práctica 30: HTTP 400, sin modificaciones:

```json
{"codigo":"frecuencia-maxima-menor","detalle":"La frecuencia máxima no puede ser menor que la habitual (30 días)"}
```

`GET /api/v1/planificacion/servicios/` devuelve un array de servicios activos periódicos
con sus configuraciones. `GET /api/v1/planificacion/servicios/{id}/propuesta/`
usa la frecuencia de la estación actual y la duración persistida.

### Pruebas y cambios de RF-PLA-001

- Existentes: `ConfigurarFrecuenciaApiTest`, `FrecuenciaDuracionIntegrationTest`,
  `ServiciosApiTest`, `AccesoTest`, `SeguridadApiTest`, `PropuestaApiTest`.
- Añadidas en `ConfiguracionServiciosApiTest`: cuatro estaciones, rechazo sin cambios
  de datos/metadatos/auditoría, dos autores, lectura por los tres roles, permisos de
  escritura, servicio inexistente/inactivo/no periódico y consumo real por programación.
- Frontend: seis casos nuevos en `services/api/planificacion.test.ts` para ambos RF:
  URL y codificación de Otoño, PUT, cuerpo snake_case, Basic, lectura nueva,
  400 de dominio/campo y propagación de 401/403. `fetch` está simulado en Vitest.
- No se corrigió código funcional de frecuencia: las verificaciones locales no
  reprodujeron fallos de sus reglas. Se preparó abajo la corrección de integración pendiente.

### Pendiente de reportes y validación final

Corrección mínima propuesta para coordinar con MOD-06: adaptar su fuente de lectura a
las tablas propietarias existentes (`mod03_frecuencia_servicio`,
`mod03_duracion_servicio`, `mod03_programacion_servicio`) conservando los nombres del
contrato `FuenteDatos`. No crear copias de catálogos ni alterar V2. El reporte también
necesita las entidades de ciclos/fechas de ejecución: el adaptador debe cubrir todas
sus dependencias antes de habilitar `DATA_SOURCE=db`.

La prueba de aceptación pendiente debe guardar una frecuencia por PUT, consultar el
reporte de un ciclo conocido, modificar la frecuencia y demostrar el cambio de su
valor/resultado; además, cambiar otra estación y demostrar que no afecta al ciclo.
No se ha implementado parcialmente este adaptador compartido ni se ha presentado su
uso de mocks como integración real.

Para solicitar Done: resolver esta dependencia (o dejar documentada con el responsable
una interpretación de alcance limitada a exposición), verificar los SHA en UAT y ejecutar
el recorrido en navegador con la Sección de Áreas Verdes, registrando fecha, participantes,
datos sintéticos, resultado esperado/obtenido y conformidad real.

## Informe RF-PLA-002 — Duración máxima por servicio

| Criterio | Clasificación | Evidencia y alcance |
|---|---|---|
| 1. Configurar y consultar duración por servicio periódico | Implementado | PUT seguido de GET nuevo sobre PostgreSQL real; DTO y UI existentes. Inactivos/inexistentes devuelven 404 y no periódicos 409. |
| 2. Autor, fecha y hora de cada modificación | Implementado | Dos cambios y dos jefes, historial de valores 10→12, autor de cada evento y metadatos de creación preservados. Instantes persistidos y consistentes. |
| 3. Rechazar nulos, cero y negativos | Implementado | `{}`, null, 0 y -1 devuelven 400 `validacion` con `errores.duracion_maxima_dias`. `@NotNull`, `@Positive` y NOT NULL/CHECK en V2. |
| 4. Conservar valor previo ante solicitud inválida | Implementado | Fila completa antes/después y número de auditorías iguales para los cuatro casos; también ante 401/403. |
| 5. Errores, permisos, persistencia y funcionamiento integrado | Implementado localmente en API; validación final pendiente | Lectura por tres roles, escritura solo jefe, consultas nuevas, 400/401/403/404/409 y programación HU-004. Con límite 2 se rechaza un ciclo de 3 días sin insertarlo; actualizado por PUT a 3 se acepta con 201. Navegador y UAT sin verificar. |

**Resultado:** técnicamente completo en el alcance local de configuración y programación.
Puede solicitarse validación final de RF-PLA-002; no equivale todavía a validación
institucional ni a evidencia del despliegue UAT.

### Archivos y métodos

- `ServiciosController.configurarDuracion`, `listar` y `propuesta`.
- `ConfiguracionServicios.NuevaDuracion`, `duracion`, `guardar`, `exigirPeriodico`.
  `dias_limpieza` permite cero; horario opcional exige fin posterior al inicio cuando
  ambos existen. Estos atributos adicionales conservan su contrato.
- `ServiciosRepository.duracion`, `ServicioPeriodico.Duracion`.
- `ProgramacionServicios.crear` y `Programador.exigirDuracion`: consumen duración y
  limpieza en la programación de HU-MOD03-004; no se han modificado.
- Migración V2 y clases de acceso/error compartidas con RF-PLA-001.
- Frontend: `DialogoDuracion.tsx`, `ServiciosPeriodicosPage.tsx`,
  `validacionPlanificacion.ts`, tipos y cliente de planificación.

### Endpoint y ejemplos

`PUT /api/v1/planificacion/servicios/{id}/duracion/` (HTTP Basic, jefe):

```json
{"duracion_maxima_dias":10,"dias_limpieza":1,"hora_inicio":"06:00","hora_fin":"12:00"}
```

HTTP 200: devuelve el servicio completo; fragmento ilustrativo de `duracion`:

```json
{"duracion_maxima_dias":10,"dias_limpieza":1,"hora_inicio":"06:00:00","hora_fin":"12:00:00","fecha_modificacion":"2026-10-28T10:00:00-05:00","modificado_por":"jefe"}
```

Con `{"duracion_maxima_dias":0}`: HTTP 400, manteniendo el registro y su auditoría:

```json
{"codigo":"validacion","detalle":"Revisa los datos","errores":{"duracion_maxima_dias":["Debe ser mayor que cero"]}}
```

La consulta es el GET de servicios compartido. No hay un GET de duración separado.

### Pruebas y cambios de RF-PLA-002

- Existentes: `ConfigurarDuracionApiTest`, `FrecuenciaDuracionIntegrationTest`,
  `AltaProgramacionApiTest`, `ProgramacionIntegrationTest`, `ProgramadorTest`,
  `AccesoTest` y `SeguridadApiTest`.
- Nuevas: `ConfiguracionServiciosApiTest` cubre conservación íntegra ante valores
  inválidos, historial con dos autores, permisos, consulta persistida, catálogo y
  cambio de límite consumido por la programación.
- Frontend: casos de contrato compartidos anteriores y un caso adicional de duración
  negativa en `validacionPlanificacion.test.ts`.
- No hubo correcciones funcionales ni cambios de contrato, secretos o migraciones.

Para Done falta ejecutar en UAT el recorrido guardar→recargar→rechazar inválidos→
comprobar el valor anterior→programar dentro/fuera del límite, con los roles reales,
y obtener la conformidad de la Sección. Registrar también la semántica actual:
la programación admite duración máxima **más días de limpieza** y cuenta días inclusivos.

## Reproducción y resultados comunes

### Entorno aislado

No reutilizar una base existente: las suites heredadas vacían tablas. Crear una nueva
instancia con nombre y puerto libres. Los valores siguientes son exclusivamente sintéticos.

```powershell
docker run -d --name konoha-auditoria-pla-20261028 -p 127.0.0.1:55439:5432 -e POSTGRES_USER=auditoria -e POSTGRES_PASSWORD=solo-pruebas-auditoria -e POSTGRES_DB=auditoria_pla_test postgis/postgis:16-3.4
docker exec konoha-auditoria-pla-20261028 pg_isready -U auditoria -d auditoria_pla_test
cd konoha-backend
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot'
$env:MOD05_TEST_DB_URL='jdbc:postgresql://127.0.0.1:55439/auditoria_pla_test'
$env:MOD05_TEST_DB_USER='auditoria'
$env:MOD05_TEST_DB_PASSWORD='solo-pruebas-auditoria'
.\mvnw.cmd -B '-Dtest=ConfiguracionServiciosApiTest' test
.\mvnw.cmd -B verify
```

`ApiPlanificacion.usuarios` crea usuarios sintéticos `jefe`, `ingeniero`, `practicante`
y `sinrol`; clave pública de pruebas `prueba-integration-local`. El nuevo
`catalogoControlado` inserta un servicio y usa el ID retornado, sin depender de IDs
del catálogo real. El reloj de programación se fija al 28/10/2026. Las marcas de
auditoría proceden del reloj de PostgreSQL; no se simulan.

Para repetir solo cobertura preexistente de los RF:

```powershell
.\mvnw.cmd -B '-Dtest=ConfigurarFrecuenciaApiTest,ConfigurarDuracionApiTest,FrecuenciaDuracionIntegrationTest,ServiciosApiTest,SeguridadApiTest,AccesoTest,PropuestaApiTest,AltaProgramacionApiTest' test
```

Frontend con Node 22 compatible (la instalación global era Node 20 y Vitest 5 requiere 22.12+):

```powershell
cd ../konoha-frontend
npm.cmd ci --ignore-scripts
npm.cmd exec --yes --package=node@22 -- node node_modules/vitest/vitest.mjs run
npm.cmd exec --yes --package=node@22 -- node node_modules/typescript/bin/tsc -b
npm.cmd exec --yes --package=node@22 -- node node_modules/oxlint/bin/oxlint
npm.cmd exec --yes --package=node@22 -- node node_modules/vite/bin/vite.js build
```

### Resultados de ejecución

- Backend, pruebas rápidas preexistentes: **114**, sin fallos ni errores.
- Backend, suite preexistente completa: **325**, sin fallos ni errores de pruebas.
  El primer `verify` detectó después un problema de formato en la nueva clase todavía
  en edición; se corrigió con `spotless:apply` exclusivamente sobre esa clase.
- Nueva clase `ConfiguracionServiciosApiTest`: **27**, sin fallos, errores ni omitidas.
- Backend final: **352 pruebas**, 0 fallos, 0 errores y 0 omitidas;
  `verify` completo correcto, incluidos empaquetado y Spotless.
- Frontend inicial: **197** pruebas. Con cobertura añadida: **204**, todas correctas,
  con Node 22.23.4; TypeScript, lint y build correctos.
- Build frontend: advertencia preexistente de bundle de más de 500 kB; no impide compilar.
- Los primeros intentos offline no pudieron resolver dependencias ausentes de Maven/npm.
  Se descargaron dependencias sin cambiar los lockfiles ni las versiones del proyecto.
- Docker y herramientas necesitaron acceso fuera del sandbox para funcionar; no se
  cambiaron permisos globales de Git (`safe.directory` se pasó por comando).

### Transporte real entre frontend y backend

Se cargaron los módulos TypeScript originales mediante Vite y se ejecutaron con Node
22.23.4 contra el JAR Spring Boot en `http://127.0.0.1:58009`, conectado al mismo
PostGIS aislado. Se utilizó `fetch` real: el adaptador del script únicamente resolvió
las URLs relativas del navegador contra esa dirección local.

Los **15 intercambios HTTP** terminaron con los estados esperados: altas/consultas
200, duración null/0/-1 y frecuencia máxima inferior 400, escritura como ingeniero
403 y lectura anónima 401. Las consultas nuevas tras cada rechazo devolvieron
exactamente la configuración anterior, incluidos autor e instante.

La comprobación SQL posterior obtuvo 21/30/45 días para Otoño, duración 10 y exactamente
dos auditorías `configurar` para el servicio sintético. Las solicitudes rechazadas no
dejaron eventos ni cambios. Esta prueba no monta componentes React ni reproduce un
recorrido visual en el navegador.

Artefactos locales ignorados por Git:

- Backend `.notas/auditoria-final.log`: resultado completo de `verify`.
- Backend `.notas/auditoria-configuracion.log`: 27 pruebas nuevas.
- Backend `.notas/fixture-transporte.sql`: fixture con guard de nombre de base,
  transacción y usuarios/servicio sintéticos; no trunca tablas.
- Backend `.notas/evidencia-persistencia.txt`: comprobación SQL posterior.
- Frontend `.notas/verificar-cliente-real.mjs` y `.notas/transporte-real.log`:
  script y resultado de los 15 intercambios HTTP.

Para repetir el transporte, una vez terminadas las suites, cargar el fixture:

```powershell
cd konoha-backend
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Get-Content -Raw -Encoding utf8 .notas/fixture-transporte.sql | docker exec -i konoha-auditoria-pla-20261028 psql -v ON_ERROR_STOP=1 -U auditoria -d auditoria_pla_test
& "$env:JAVA_HOME/bin/java.exe" -jar target/areas-verdes-backend-0.1.0.jar --server.port=58009 --spring.config.import= --spring.datasource.url=jdbc:postgresql://127.0.0.1:55439/auditoria_pla_test --spring.datasource.username=auditoria --spring.datasource.password=solo-pruebas-auditoria --mod06.data-source=mock --mod06.gemini-api-key= --mod06.gemini-model=
```

En otra consola, desde el frontend:

```powershell
npm.cmd exec --yes --package=node@22 -- node .notas/verificar-cliente-real.mjs
```

Al importar el fixture, el primer intento de PowerShell perdió la `ó` del rol al
canalizar texto; se corrigió fijando UTF-8 y se volvió a cargar correctamente dentro
de una transacción. Fue un fallo del comando de preparación, no del endpoint.
Los scripts de `.notas/` son artefactos locales de sesión; la reproducción automatizada
que permanece en Git son las clases de pruebas y sus fixtures de `@BeforeEach`.
Al terminar se detuvieron la API temporal y el contenedor de auditoría, conservando
los datos sintéticos. Para reutilizar esa instancia: `docker start konoha-auditoria-pla-20261028`.

### Límites de evidencia

MockMvc ejecuta filtros, controllers, validaciones, servicio, transacciones y SQL reales;
no abre un navegador. Vitest verifica el contrato del cliente con transporte simulado;
la comprobación adicional descrita arriba utiliza HTTP real.
No se han validado visualmente los diálogos, accesibilidad, interacción en navegador,
usuarios institucionales, UAT ni aceptación de la Sección. No se ha declarado Done en Linear.
