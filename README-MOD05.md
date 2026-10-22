# MOD-05 — Spring Boot / Java

Backend independiente para RF-SER-003, RF-SER-004, RF-SER-005, RF-SER-006 y RF-SER-011. Reemplaza la app Django `tercerizados`. Comparte el back-end `backend/` con el MOD-06, que conserva su comportamiento anterior.

## Arranque local

Requisitos: JDK 21 y PostgreSQL 14 o posterior. No hace falta instalar Maven: `./mvnw` (macOS/Linux) o `mvnw.cmd` (Windows) descargan Maven 3.9.11 la primera vez en `~/.m2/wrapper`, verifican su SHA-512 y lo ejecutan; si prefieres tu propio Maven (3.6.3 o posterior), cambia `./mvnw` por `mvn`. `JAVA_HOME` debe apuntar al JDK 21; comprobar `java -version` y `./mvnw -version`. En macOS con Homebrew, por ejemplo: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.

Crear una **base nueva**, por ejemplo `areas_verdes_mod05`, y un usuario propietario. No reutilizar el esquema Django ni sus usuarios. No se realiza conversión de datos antiguos.

Desde `backend/`:

```sh
cp .env.example .env
# Editar usuario, contraseña y URL PostgreSQL en .env
./mvnw spring-boot:run
```

Spring importa `.env` como properties (sin comillas ni `export`). Las variables del entorno también son válidas. Flyway aplica automáticamente `src/main/resources/db/migration/V1__mod05.sql` en la base nueva. Este SQL incluye todas las tablas, restricciones, índices, usuarios/roles y triggers de auditoría; no depende de Django ni de PostGIS. El esquema permanece gestionado por Flyway: no ejecutar el script manualmente después del arranque.

Para precargar los catálogos y usuarios demostrativos, configurar `MOD05_DEMO_PASSWORD` (al menos 12 caracteres) y ejecutar:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--mod05.seed=true
```

La precarga solo se activa con el perfil `dev` y el flag explícito. Usuarios: `mod05_jefe`, `mod05_ingeniero`, `mod05_practicante`, con la contraseña indicada. No se imprime la contraseña. Repetir la precarga no cambia usuarios/roles/contraseñas existentes y rechaza colisiones en los catálogos en vez de sobrescribirlas. No importa atenciones históricas. Conserva las vigencias originales de los contratos de ejemplo, válidas en 2026.

- Servicio: `http://localhost:8000`.
- API: `http://localhost:8000/api/tercerizados/`.
- Swagger: `http://localhost:8000/api/tercerizados/docs` (documentación común, también en `/api/docs`).
- OpenAPI: `http://localhost:8000/api/tercerizados/schema` (también en `/api/schema`).

Para generar el ejecutable sin correr pruebas de integración:

```sh
./mvnw -DskipTests package
java -jar target/mod05-0.1.0.jar
```

## Arquitectura y compatibilidad

Java 21 / Spring Boot 3.5.16; Spring Security con HTTP Basic y contraseñas BCrypt; Spring JDBC con consultas parametrizadas; Flyway y PostgreSQL. Se usa JDBC explícito para conservar las restricciones y bloqueos SQL del dominio, sin introducir un ORM adicional. DTO de entrada validados, controladores REST, servicios transaccionales, repositorio y almacenamiento privado están separados. Los catálogos son proyecciones locales, pendientes de integrar con sus módulos propietarios.

Se mantiene el contrato consumido por React:

- Rutas con barra final (también se aceptan sin ella), campos `snake_case`, estados en español.
- `GET me/`: `{id, usuario, nombre, rol}`. Devuelve el rol de mayor jerarquía o `null` para usuarios válidos sin rol del módulo.
- HTTP 403 ante credenciales inválidas/ausentes y permisos insuficientes; 400 validación, 404 recurso/página inexistente, 409 transición incompatible y 503 almacenamiento no disponible.
- Errores de dominio `{codigo, detalle}`; validación por campo como listas de mensajes; el frontend ya interpreta ambos.
- Paginación de 25: `{count, next, previous, results}`. `metrado` se devuelve como texto decimal de dos posiciones. Los nombres descriptivos de relaciones están incluidos.
- Fechas `YYYY-MM-DD` e instantes ISO con zona; reglas de calendario en `America/Lima`.
- Multipart conserva `documentos` como texto JSON y `archivos` repetido en el mismo orden.

Los usuarios nuevos son independientes de los anteriores de Django. La sesión guardada en el navegador puede contener credenciales antiguas: cerrar sesión y volver a ingresar con los usuarios precargados de Spring.

El frontend conserva `VITE_API_TERCERIZADOS_URL=/api/tercerizados`. Su proxy Vite envía `/api/tercerizados`, `/swagger-ui` y las demás rutas `/api` al back-end en 8000. Para un despliegue se necesita el mismo enrutamiento en el reverse proxy. No se habilita CORS abierto. HTTP Basic se usa sobre HTTPS fuera de localhost; no hay sesiones/cookies ni endpoint que emita tokens. La futura integración con MOD-01 queda pendiente.

## API y reglas

| Ruta (relativa a `/api/tercerizados/`) | Métodos | Escritura |
|---|---|---|
| `me/` | GET | — |
| `atenciones/` | GET, POST | Jefe |
| `atenciones/{id}/` | GET | — |
| `atenciones/{id}/orden-compra/` | PATCH | Jefe, ingeniero, practicante autorizado |
| `atenciones/{id}/inicio/` | POST | Los tres roles |
| `atenciones/{id}/fin/` | POST | Los tres roles |
| `atenciones/{id}/origen/` | PATCH | Jefe |
| `atenciones/{id}/acuerdo-unidad/` | PATCH | Jefe |
| `atenciones/{id}/reportes/` | GET, POST multipart | Los tres roles |
| `atenciones/{id}/reportes/{reporte}/` | GET | — |
| `atenciones/{id}/reportes/{reporte}/documentos/{documento}/descarga/` | GET | — |

Los grupos conservan exactamente los nombres `Jefe de sección`, `Ingeniero de campo`, `Practicante autorizado`. Solo estos tres roles pueden consultar el módulo; `/me/` admite a cualquier usuario activo autenticado para informar si tiene acceso.

Ejemplo de registro:

```json
{
  "contrato_id": 1,
  "descripcion": "Corte de césped",
  "origen": "Demanda",
  "alcances": [
    {"area_verde_id": 1, "metrado": "120.00", "unidad_medida_id": 3},
    {"ejemplar_id": 1}
  ]
}
```

Contrato vigente, proveedor y servicio activos, ejecutor tercerizado. El servicio se deriva del contrato. Cada alcance indica un área o ejemplar existente, sin duplicados, con metrado no negativo y unidad juntos o ninguno. Código UUID generado por el servidor. Crear atención, alcances e historial es atómico.

Estados: `Requerimiento registrado` → `OC referenciada` → `En ejecución` → `Ejecutado pendiente de reporte` → `Reporte adjunto`.

```text
PATCH orden-compra/  {"numero_orden_compra":"OC-2026-001"}
POST  inicio/        {"fecha_hora_inicio":"2026-10-21T08:00:00-05:00"}
POST  fin/           {"fecha_hora_fin":"2026-10-21T12:00:00-05:00"}
```

Corregir OC exige motivo y solo se permite antes del inicio. No se emiten órdenes. El fin no precede al inicio. La marca de riesgo se hereda del catálogo o se activa al registrar; no puede desactivarse desde la petición. Iniciar atenciones de riesgo sigue bloqueado hasta integrar seguridad.

Origen obligatorio: `Demanda`, `ProgramacionEstacional`, `SolicitudUnidad`. Para solicitud de unidad, enviar exclusivamente `unidad_solicitante_id` o `unidad_provisional` (nombre). La creación/reutilización de la unidad provisional es atómica. Corregir origen requiere motivo y solo puede hacerse antes del inicio. Los demás orígenes no admiten unidad.

Después de finalizar una solicitud de unidad:

```json
{"acuerdo_unidad": false, "fecha_acuerdo_unidad": "2026-10-21", "comentarios_acuerdo_unidad": "Solicita revisión"}
```

`null` con fecha `null` significa pendiente. El acuerdo comunicado necesita fecha no anterior al fin. No cambia el estado ni concede conformidad. Cada cambio deja auditoría; la base impide actualizar/eliminar auditoría e historial.

### Búsqueda y catálogos

Atenciones: `q`, `estado`, `origen`, `contrato_id`, `page`. Búsqueda global antes de paginar, sin distinguir mayúsculas, hasta 100 caracteres y ocho palabras; cada palabra debe aparecer en al menos un campo buscable. Se busca en código, descripción, OC, contrato, proveedor, servicio y unidad.

Catálogos de solo lectura bajo `catalogos/`, todos con `q` y `page`:

| Catálogo | Filtros adicionales |
|---|---|
| `proveedores/` | `activo` |
| `contratos/` | `vigente`, `proveedor_id`, `tipo_servicio_id` |
| `tipos-servicio/` | `activo`, `es_riesgo`, `ejecutor` |
| `areas-verdes/` | `activo` |
| `ejemplares/` | `area_verde_id` |
| `unidades-medida/` | — |
| `unidades-institucionales/` | `activo`, `provisional` |

Booleanos aceptan `true/false` o `1/0`. Filtros vacíos se omiten; valores inválidos dan 400. Página inexistente da 404; `page=last` selecciona la última.

### Documentos

Solo después de finalizar, con fecha de recepción no anterior al fin. Cada carga crea una revisión completa nueva y exige al menos un `InformeFinal`. Categorías: `InformeFinal`, `Metrado`, `PlantasAtendidas`, `FichasDiarias`, `CharlasTecnicas`, `PlagasAtendidas`, `ProductosAplicados`, `EvidenciaEspacio`, `RutaRecorrida`. La evidencia por espacio exige `alcance_id` de esa misma atención.

```sh
curl --user mod05_jefe \
  -F 'fecha_recepcion=2026-10-21' \
  -F 'documentos=[{"categoria":"InformeFinal"},{"categoria":"EvidenciaEspacio","alcance_id":1}]' \
  -F 'archivos=@informe.pdf' -F 'archivos=@jardin.jpg' \
  http://localhost:8000/api/tercerizados/atenciones/1/reportes/
```

PDF, JPEG, PNG, DOCX y XLSX, 20 MB por archivo y 100 MB por petición por defecto; límites configurables. Se valida cabecera/estructura básica y se calcula SHA-256. Validación de formato no equivale a análisis antimalware. Documentos Office con expansión superior a 100 MB o más de 10.000 entradas se rechazan.

Archivos fuera de la BD con nombres UUID. El directorio no se publica como recurso estático; descargas autenticadas como adjuntos sin caché pública. Revisiones previas permanecen disponibles. Bloqueo de la atención serializa cambios de estado y numeración de reportes. Si falla la escritura o el commit, se limpian archivos nuevos; fallos de limpieza se registran en logs. Una terminación abrupta puede dejar huérfanos y necesita reconciliación operativa.

## Pruebas

Se ejecutan contra PostgreSQL real, nunca H2. Crear una base exclusiva terminada en `_test`:

```sh
createdb -h localhost -U tu_usuario mod05_spring_test
export MOD05_TEST_DB_URL=jdbc:postgresql://localhost:5432/mod05_spring_test
export MOD05_TEST_DB_USER=tu_usuario
export MOD05_TEST_DB_PASSWORD=tu_clave
./mvnw test
```

**La suite vacía las tablas de esa base** entre casos. El guard de configuración exige el sufijo `_test`. Por defecto utiliza `127.0.0.1:55432/mod05_spring_test`, el usuario del sistema y contraseña vacía. Flyway comprueba/aplica el esquema. Incluye autenticación, contrato frontend, filtros, alcances, estados, documentación, rollback durante commit, triggers, precarga y concurrencia real.

Las pruebas del MOD-06 corren en el mismo `./mvnw test` y usan la misma base `_test`. Frontend desde `../frontend/`: `npm test`, `npm run build`, `npm run lint`.

## TODO conservados

- Registro/mantenimiento de proveedores y contratos; hoy precarga y consulta.
- Formularios estructurados y completitud documental por servicio, previa validación con el usuario.
- Integrar catálogos, usuarios, incidencias y programación con los módulos propietarios sin duplicar IDs.
- Integrar almacenamiento de objetos y seguridad para atenciones de riesgo.
- Fuera de esta entrega: extraordinarias, órdenes de servicio, conformidad, subsanación, IA y nuevas pantallas frontend.
