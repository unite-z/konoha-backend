# MOD-05 — Atenciones tercerizadas

Implementa RF-SER-003/004/005/006/011 mediante Django REST Framework y PostgreSQL. El MOD-06 sigue usando su fuente de datos configurada, incluidos sus mocks. No se cambian sus permisos ni se permite escribir atenciones mediante sus fuentes.

## Arranque

Desde `backend/`, con las dependencias de `requirements.txt` instaladas, configurar `.env`:

```dotenv
DJANGO_SECRET_KEY=una-clave-local-propia
DJANGO_DEBUG=True
DJANGO_ALLOWED_HOSTS=localhost,127.0.0.1
DATA_SOURCE=mock
TERCERIZADOS_ENABLED=True
POSTGRES_DB=areas_verdes
POSTGRES_USER=areas_verdes
POSTGRES_PASSWORD=contraseña-local
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
TERCERIZADOS_ARCHIVOS_DIR=/ruta/privada/documentos
TERCERIZADOS_MAX_ARCHIVO_BYTES=20971520
```

Crear previamente la base PostgreSQL y su usuario. No es necesario PostGIS para estas proyecciones de catálogo (no almacenan geometrías). MOD-05 se habilita expresamente; si `TERCERIZADOS_ENABLED=False`, sus rutas no se publican y el arranque mock original no requiere base de datos.

```sh
python manage.py migrate
python manage.py precargar_tercerizados --usuarios
python manage.py changepassword mod05_jefe
python manage.py changepassword mod05_ingeniero
python manage.py changepassword mod05_practicante
python manage.py runserver
```

La precarga solo admite `DEBUG=True`. Reutiliza IDs de JSON, valida referencias, actualiza secuencias y revierte toda la operación ante errores o diferencias con registros existentes. No sobrescribe catálogos, roles de usuarios existentes ni contraseñas. Los contratos conservan su vigencia original; no se extienden para que las pruebas manuales pasen fuera de 2026. No se importan atenciones históricas.

Los usuarios nuevos tienen contraseña inutilizable hasta ejecutar `changepassword`. Los grupos son `Jefe de sección`, `Ingeniero de campo` y `Practicante autorizado`; pertenecer al grupo `Practicante` no concede acceso. Administradores sin uno de estos grupos tampoco reciben acceso implícito.

Autenticación disponible para esta entrega: HTTP Basic con usuarios Django (usar HTTPS fuera de localhost). Las vistas aceptan también sesiones si el proyecto integra el middleware de sesiones y autenticación posteriormente; el borrador no publica un login de sesión. Ningún endpoint del módulo admite usuarios anónimos o inactivos. Los errores de autenticación/permisos responden 403.

## API

Swagger: `/api/docs/`; esquema: `/api/schema/`. Base: `/api/tercerizados/`. Listas paginadas de 25 registros con `count`, `next`, `previous`, `results`.

| Ruta | Método | Rol de escritura |
|---|---|---|
| `me/` | GET | Cualquier usuario autenticado |
| `atenciones/` | GET, POST | Jefe |
| `atenciones/{id}/` | GET | — |
| `atenciones/{id}/orden-compra/` | PATCH | Jefe, ingeniero, practicante autorizado |
| `atenciones/{id}/inicio/` | POST | Jefe, ingeniero, practicante autorizado |
| `atenciones/{id}/fin/` | POST | Jefe, ingeniero, practicante autorizado |
| `atenciones/{id}/origen/` | PATCH | Jefe |
| `atenciones/{id}/acuerdo-unidad/` | PATCH | Jefe |
| `atenciones/{id}/reportes/` | GET, POST multipart | Jefe, ingeniero, practicante autorizado |
| `atenciones/{id}/reportes/{reporte_id}/` | GET | — |
| `atenciones/{id}/reportes/{reporte_id}/documentos/{documento_id}/descarga/` | GET | — |

### Usuario actual (`GET me/`)

Devuelve `{id, usuario, nombre, rol}`. `rol` es el de mayor jerarquía entre `Jefe de sección`, `Ingeniero de campo` y `Practicante autorizado`; es `null` si el usuario es válido pero no pertenece a ninguno (no tiene acceso al resto del módulo). Credenciales ausentes o inválidas, y usuarios inactivos, responden 403. Permite a un cliente comprobar credenciales al ingresar y decidir qué acciones mostrar; el servidor sigue validando cada operación.

### Filtros y búsqueda

Todos los listados aceptan `page`. Los parámetros desconocidos se ignoran, un valor vacío equivale a no filtrar y un valor no válido responde 400 con `{parametro: [mensaje]}`. La búsqueda `q` (máx. 100 caracteres) exige que cada palabra aparezca en alguno de los campos indicados, sin distinguir mayúsculas; distingue tildes. Los filtros se combinan con AND y la paginación cuenta solo los registros filtrados.

| Listado | `q` busca en | Filtros |
|---|---|---|
| `atenciones/` | código, descripción, OC, número de contrato, proveedor, servicio, unidad solicitante | `estado`, `origen`, `contrato_id` |
| `catalogos/contratos/` | número, proveedor, servicio | `vigente` (fecha de Lima), `proveedor_id`, `tipo_servicio_id` |
| `catalogos/proveedores/` | razón social | `activo` |
| `catalogos/tipos-servicio/` | nombre | `activo`, `es_riesgo`, `ejecutor` |
| `catalogos/areas-verdes/` | nombre | `activo` |
| `catalogos/ejemplares/` | código, área verde | `area_verde_id` |
| `catalogos/unidades-medida/` | nombre, símbolo | — |
| `catalogos/unidades-institucionales/` | nombre | `activo`, `provisional` |

Booleanos: `true`/`1` o `false`/`0`. `vigente=true` solo mira fechas; la creación vuelve a validar proveedor y servicio activos y modalidad tercerizada. Los catálogos se ordenan por nombre o código (luego por `id`).

### Nombres en las respuestas

Además de los IDs (que se conservan), las respuestas traen nombres para no resolver catálogos en el cliente: atenciones (`contrato_numero`, `proveedor_id`, `proveedor_nombre`, `tipo_servicio_nombre`, `unidad_solicitante_nombre`, `unidad_solicitante_provisional`, `solicitado_por_nombre`, `usuario_creacion_nombre`), alcances (`area_verde_nombre`, `ejemplar_codigo`, `ejemplar_area_verde_nombre`, `unidad_medida_nombre`, `unidad_medida_simbolo`), historial (`usuario_nombre`), reportes (`usuario_creacion_nombre`), contratos (`proveedor_nombre`, `tipo_servicio_nombre`) y ejemplares (`area_verde_nombre`). El nombre de un usuario es su nombre completo o, si no lo tiene, su usuario. Alcances e historial salen en orden estable (por `id`; historial por fecha).

Los tres roles pueden consultar. Los catálogos de solo lectura están bajo `catalogos/`: `proveedores/`, `contratos/`, `tipos-servicio/`, `areas-verdes/`, `ejemplares/`, `unidades-medida/`, `unidades-institucionales/`. No se exponen importes económicos ni rutas privadas de archivos.

Ejemplo POST `atenciones/` (IDs a elegir desde los catálogos):

```json
{
  "contrato_id": 1,
  "descripcion": "Corte de césped en los espacios seleccionados",
  "origen": "Demanda",
  "alcances": [
    {"area_verde_id": 1, "metrado": "120.00", "unidad_medida_id": 3},
    {"ejemplar_id": 1}
  ]
}
```

Cada alcance indica exclusivamente un área o un ejemplar. No se duplican destinos; incluir un área no selecciona automáticamente sus ejemplares. Metrado no negativo y unidad deben indicarse juntos. La atención nace con clase `ProgramadaContrato`; código `AT-` más UUID hexadecimal, y servicio derivado del contrato vigente. `es_riesgo` puede marcarse en la petición; si el catálogo marca el servicio de riesgo, no puede desactivarse desde la petición. El inicio de cualquier atención de riesgo queda bloqueado mientras no exista integración de seguridad.

Origen obligatorio: `Demanda`, `ProgramacionEstacional` o `SolicitudUnidad`. Este último requiere `unidad_solicitante_id` o `unidad_provisional` (nombre), exclusivamente uno. La unidad provisional se crea/reutiliza de forma transaccional. Los demás orígenes no admiten unidad. Para corregir origen antes del inicio se envía el nuevo origen, su unidad si aplica y `motivo` no vacío.

Secuencia de operaciones:

```text
PATCH orden-compra/  {"numero_orden_compra":"OC-2026-001"}
POST  inicio/        {"fecha_hora_inicio":"2026-10-21T08:00:00-05:00"}
POST  fin/           {"fecha_hora_fin":"2026-10-21T12:00:00-05:00"}
```

La corrección de OC requiere `motivo` y solo se permite antes del inicio. No se emiten ni aprueban órdenes. El inicio requiere OC; el fin requiere inicio y no puede precederlo. Los estados son `Requerimiento registrado`, `OC referenciada`, `En ejecución`, `Ejecutado pendiente de reporte`, `Reporte adjunto`.

El acuerdo se registra solo después del fin y para solicitudes de unidad:

```json
{"acuerdo_unidad": false, "fecha_acuerdo_unidad": "2026-10-21", "comentarios_acuerdo_unidad": "Solicita revisar el área intervenida"}
```

`acuerdo_unidad: null` y `fecha_acuerdo_unidad: null` representan pendiente. Un acuerdo comunicado exige fecha no anterior al fin. Las modificaciones se auditan; no cambian el estado ni conceden conformidad.

## Documentos privados

El informe final se recibe después de terminar la atención. Cada POST crea una revisión completa nueva, sin eliminar las anteriores. Su fecha de recepción no precede al fin. Campos multipart:

- `fecha_recepcion`: fecha ISO.
- `resumen`: opcional.
- `documentos`: texto JSON con una lista de `{categoria, alcance_id?, descripcion?}`.
- `archivos`: campo repetido, un archivo por elemento de `documentos`, en el mismo orden.

Categorías: `InformeFinal`, `Metrado`, `PlantasAtendidas`, `FichasDiarias`, `CharlasTecnicas`, `PlagasAtendidas`, `ProductosAplicados`, `EvidenciaEspacio`, `RutaRecorrida`. Cada revisión exige al menos un `InformeFinal`. `EvidenciaEspacio` exige el ID de un alcance de esa atención. No se comprueba la completitud semántica de los documentos ni el formato por servicio.

```sh
curl --user mod05_jefe \
  -F 'fecha_recepcion=2026-10-21' \
  -F 'documentos=[{"categoria":"InformeFinal"},{"categoria":"EvidenciaEspacio","alcance_id":1}]' \
  -F 'archivos=@informe.pdf' -F 'archivos=@jardin.jpg' \
  http://localhost:8000/api/tercerizados/atenciones/1/reportes/
```

Tipos: PDF, JPEG, PNG, DOCX y XLSX; máximo predeterminado 20 MB por archivo. Se verifica extensión, cabecera/estructura básica y se calcula SHA-256; esto no sustituye un análisis antimalware. El almacenamiento usa nombres UUID y nunca sobrescribe revisiones. Las descargas se sirven como adjuntos autenticados, sin caché pública. No publicar el directorio mediante `MEDIA_URL`, nginx o rutas estáticas.

Ante errores de validación no se escribe nada. Ante errores de persistencia/carga se revierte la transacción y se eliminan los archivos nuevos; si la limpieza también falla, se registra el nombre huérfano en logs para recuperación. Una caída abrupta del proceso puede dejar archivos huérfanos: no existe una transacción distribuida entre disco y PostgreSQL.

## Persistencia y migraciones

`models/referencias.py` contiene proyecciones mínimas de catálogos; `models/operacion.py`, las entidades propias. No reemplazan los modelos completos de MOD-01/02/03/04. Los servicios concentran reglas/transacciones; `repositorios.py` concentra consultas compartidas y bloqueos. Las vistas validan, invocan y serializan.

Las migraciones Django son la fuente de verdad. `sql/0001_tercerizados.sql` equivale a `0001_initial` y `0002_historial_inmutable`, y requiere las tablas de autenticación Django previamente migradas. Incluye restricciones, índices y triggers que impiden UPDATE/DELETE sobre auditoría e historial. No se publican endpoints de eliminación de registros operativos.

Regenerar el SQL después de cambiar migraciones:

```sh
python manage.py exportar_sql_tercerizados > tercerizados/sql/0001_tercerizados.sql
```

**No ejecutar el SQL después de `migrate`**. Para una instalación alternativa mediante SQL, preparar primero `python manage.py migrate auth`, aplicar el script a un esquema sin tablas MOD-05, comprobar su equivalencia y solo entonces registrar ambas migraciones con `python manage.py migrate tercerizados 0002 --fake`. `--fake` no valida el esquema y no debe usarse para ocultar diferencias.

## Verificación

Con PostgreSQL y MOD-05 habilitado, el usuario de pruebas debe poder crear la base temporal `test_<POSTGRES_DB>`:

```sh
pytest tercerizados/tests reportes_indicadores/tests
python manage.py check
python manage.py makemigrations --check --dry-run
python manage.py spectacular --file /tmp/esquema.yaml --validate
```

Las pruebas cubren API, autenticación real, permisos, restricciones, auditoría inmutable, versiones/documentos, errores de almacenamiento, precarga idempotente, concurrencia real y equivalencia del esquema SQL. No llaman a Gemini. Para ejecutar solo la regresión original sin BD: `TERCERIZADOS_ENABLED=False DATA_SOURCE=mock pytest reportes_indicadores/tests`.

## TODO y límites

- Registrar/mantener proveedores y contratos por API; hoy solo precarga y consulta.
- Búsqueda insensible a tildes (requiere la extensión `unaccent` de PostgreSQL); hoy distingue tildes.
- Exponer el registro de auditoría (motivo y valores anteriores de correcciones de OC y origen); hoy solo se guarda. Urgente; probablemente de otro módulo.
- Formularios estructurados y completitud por servicio, después de validarlos con el usuario.
- Integrar usuarios, referencias y catálogos con los módulos propietarios mediante una migración de IDs, sin duplicarlos.
- Integrar origen con incidencias/programación y almacenamiento de objetos.
- Integrar revisión de seguridad; no hay endpoint para omitir el bloqueo de riesgo.
- Fuera de entrega: frontend, atención extraordinaria, órdenes de servicio, conformidad, subsanación e IA. El desacuerdo de unidad queda visible y auditado, sin abrir una subsanación automáticamente.
