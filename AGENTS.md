# konoha-backend

API en Spring Boot 3.5.16 y Java 21 del sistema de gestión de áreas verdes del campus PUCP (DP2). Un solo servicio para todos los módulos, en el puerto 8000.

Este fichero es la guía para personas y agentes. Estándares comunes y ADR: en el repo `konoha-infra` (`docs/estandares/` y `docs/adr/`).

## Estado

- Sprint 0 (en curso): repos, estándares, CI y despliegue preparados. Aún sin EC2: `deploy-uat.yml` se salta hasta que `DESPLIEGUE_ACTIVO=true`.
- MOD-05 y MOD-06: borrador importado y funcionando en local. MOD-06 lee datos `mock`.
- Pendiente de arranque: tablas de MOD-01 (`core_`) y MOD-02, PostGIS y almacenamiento en S3.

## Mapa

| Ruta | Qué hay |
|---|---|
| `src/main/java/pe/edu/pucp/areasverdes/mod05/` | MOD-05 «Atenciones tercerizadas»: `/api/tercerizados/`, HTTP Basic, JDBC y Flyway |
| `src/main/java/pe/edu/pucp/areasverdes/mod06/` | MOD-06 «Reportes, indicadores e IA»: `/api/v1/`, datos `mock` o `db`, Gemini |
| `src/main/java/pe/edu/pucp/areasverdes/comun/` | Lo compartido entre módulos (hoy, la seguridad de `/api/health`) |
| `src/main/resources/db/migration/` | Migraciones Flyway |
| `src/main/resources/prompts/` | Prompts de la IA |
| `src/test/java/.../apoyo/` | Dobles de prueba y constructores de datos |
| `mock-data/` | Datos simulados (JSON y PDF) para `DATA_SOURCE=mock` |
| `Dockerfile` | Imagen `konoha-api`: build con Maven y JRE 21 sin root |
| `.github/workflows/` | `ci.yml`, `deploy-uat.yml` y `limpiar-imagenes.yml` |

## Comandos

```sh
./mvnw spring-boot:run                            # arranca en :8000 (lee .env)
./mvnw verify                                     # todo: formato, compilación y pruebas
./mvnw -Ppruebas-rapidas test                     # pruebas sin Spring ni base (segundos)
./mvnw spotless:apply                             # formatea lo que has cambiado
./mvnw test -Dtest=CumplimientoCorteServiceTest   # una clase
```

Java 21 exactamente. Las pruebas de integración necesitan PostGIS y `MOD05_TEST_DB_*` (ver `docs/estandares/pruebas.md`).

## Gates: lo que tiene que pasar antes de pushear

Es lo mismo que exige `ci-ok`:

```sh
./mvnw verify                                     # con PostGIS levantado y MOD05_TEST_DB_* exportadas
```

- El hook `pre-push` ya ejecuta Spotless sobre lo cambiado y las pruebas rápidas. Actívalo con `git config core.hooksPath .githooks`.
- En el PR, CI comprueba además el título, la rama, los nombres de fichero, los artefactos y los secretos (gitleaks).

## Reglas no negociables

- **Nunca se edita una migración ya fusionada.** Se corrige con una nueva. Toda migración tiene que ser compatible con la versión anterior de la api (expand/contract).
- **Cada entidad tiene un módulo dueño** (usuarios y roles: MOD-01 con tablas `core_`; áreas verdes y ejemplares: MOD-02). Nunca crees una copia de una tabla ajena: referénciala. Ver `docs/estandares/base-datos.md`.
- **SQL siempre parametrizado.** Ninguna entrada del usuario se concatena en una consulta.
- **El backend decide los permisos.** Todo endpoint nuevo entra en la matriz de permisos por rol, o CI falla.
- **Las pruebas nunca llaman a Gemini.** Se usa `ClienteIAFalso`.
- **Ningún secreto en el repo.** `.env.example` lleva los valores vacíos.
- Cada endpoint nuevo:
  - va bajo `/api/v1/<modulo>/`;
  - está documentado en OpenAPI;
  - devuelve los errores en el formato de `docs/estandares/api.md`;
  - es idempotente si lo usa la cola sin conexión de la PWA.
- JSON en snake_case (ADR 0005), por la configuración global de Jackson. Nada de `@JsonProperty` para renombrar ni de componentes Java en snake_case en código nuevo.
- Archivos: a través de `ArchivoStorage`, nunca directamente a disco o a S3. En S3, el guard de `S3_PREFIX` es obligatorio.
- Fechas en `America/Lima` y con `java.time`. Importes y medidas en `BigDecimal`.
- Inyección por constructor, DTO como `record`, `@Transactional` en el servicio.
- Las pruebas que levantan Spring terminan en `IntegrationTest` o `ApiTest`, para que el perfil `pruebas-rapidas` las excluya.
- Nombres:
  - clases en PascalCase y paquetes en minúsculas;
  - claves de configuración propias en kebab-case;
  - variables de entorno en SCREAMING_SNAKE_CASE;
  - migraciones `V<n>__<modulo>_<descripcion>.sql`;
  - el resto, en kebab-case.
- Todo en español de España: código de dominio, mensajes, docs y commits.

## Decisiones cerradas (no se re-discuten)

Cambiarlas exige un ADR nuevo que sustituya al anterior.

| Decisión | Dónde |
|---|---|
| Backend en Spring Boot 3.5 con Java 21 | `konoha-infra`: `docs/adr/0001-backend-spring-boot.md` |
| Tres repos: backend, frontend e infra | `konoha-infra`: `docs/adr/0002-repos-backend-frontend-infra.md` |
| Una EC2 con Docker Compose; entornos UAT y prod, sin DEV en la nube | `konoha-infra`: `docs/adr/0003-despliegue-ec2-compose.md` |
| Alertas solo dentro de la aplicación (SES denegado en el lab) | `konoha-infra`: `docs/adr/0003-despliegue-ec2-compose.md` |
| Flyway con numeración correlativa y expand/contract | `docs/estandares/base-datos.md` |
| Propiedad de entidades por módulo (`core_` para MOD-01) | `docs/estandares/base-datos.md` |
| Claves JSON y parámetros de la API en snake_case | `konoha-infra`: `docs/adr/0005-claves-json-snake-case.md` |
| Prefijo de tickets `AV`; el primer issue es AV-1 | `konoha-infra`: `docs/estandares/git-y-prs.md` |

Pendiente de decisión (no la cambies por tu cuenta): formato de errores ProblemDetail (ADR 0004, propuesto).

## Seguridad abierta: no empeorar

- MOD-05 usa HTTP Basic con usuarios propios, hasta que exista la autenticación de MOD-01.
- MOD-06 no tiene autenticación (`/api/v1/**` abierto).
- Archivos en el disco del contenedor, sin el guard de `S3_PREFIX`.
- MOD-05 responde 403 cuando faltan credenciales (debería ser 401).
- Swagger (`/api/docs`) es público en todos los entornos.

Ningún PR puede ampliar estas brechas (por ejemplo, abrir más rutas sin autenticación). Cerrarlas va en tickets propios.

## Deuda conocida (no la arregles de paso: tiene ticket propio)

- Dos formatos de error (MOD-05 `{codigo, detalle}`; MOD-06 emula DRF en `api/drf`). Objetivo: ProblemDetail (ADR 0004).
- MOD-05 nombra los componentes de sus `record` en snake_case y MOD-06 serializa a mano con mapas (`Serializadores`). Con la estrategia global de Jackson, ninguna de las dos cosas hace falta. Se simplifican cuando se toquen, sin cambiar el contrato.
- Dos configuraciones de seguridad (`mod05/Seguridad` y `mod06/config/SeguridadMod06`) y la zona `LIMA` definida dos veces (`AreasVerdesApplication` y `Mod05Application`).
- Variables con prefijo de módulo (`MOD05_DB_*`) para una base compartida.
- MOD-05 en `/api/tercerizados/`, fuera de `/api/v1/`.
- `V1__mod05.sql` duplica entidades ajenas (`mod05_usuario`, `mod05_usuario_rol`, `mod05_areaverde`, `mod05_ejemplar`). Objetivo: consolidarlas en las tablas de su dueño.
- Formato: está previsto un PR único de `spotless:apply` sobre todo el código (ver `CONTRIBUTING.md`).

## Qué no entra al repo

- `.env` (solo `.env.example`), credenciales y tokens.
- Volcados de base de datos y datos reales de la sección.
- PDF de entregables del curso y capturas con datos personales.
- Salidas de build (`target/`), `private-media/` y binarios de más de 5 MB.

## Planes y artefactos de sesión

Los planes, notas y borradores de trabajo de personas y agentes van en `.notas/` (ignorada por git) o fuera del repo. Nunca se commitean. Lo que deba perdurar se pasa a uno de los documentos de abajo.

## Mapa de documentos

| Documento | Para qué |
|---|---|
| `AGENTS.md` | Esta guía: estado, gates, reglas y decisiones |
| `CLAUDE.md` | Solo importa `AGENTS.md` |
| `README.md` | Qué es y cómo arrancarlo en dos minutos |
| `CONTRIBUTING.md` | Ciclo de trabajo y checklist de revisión del backend |
| `SECURITY.md` | Cómo avisar de una vulnerabilidad |
| `docs/arranque-local.md` | Instalación paso a paso en macOS y Windows |
| `docs/estandares/*.md` | Java, API, base de datos, pruebas y almacenamiento |
| `docs/modulos/mod05.md` | Documentación funcional de MOD-05 |
| `mock-data/README.md` | Formato de los datos simulados |

**No se crean documentos nuevos** sin fusionar antes su contenido en uno existente o justificar en el PR por qué no cabe.

## Flujo de trabajo

Rama `<feat|fix|chore>/<ID>-<descripcion>`, commit `tipo(ID): descripción`, PR con squash. Al fusionar en `main` se despliega en UAT.
