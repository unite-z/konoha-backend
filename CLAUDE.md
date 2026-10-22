# konoha-backend

API en Spring Boot 3.5.16 y Java 21 del sistema de gestión de áreas verdes del campus PUCP (DP2). Un solo servicio para todos los módulos, en el puerto 8000.

Estándares comunes (nombres, git y PR, seguridad) y ADR: en el repo `konoha-infra`, en `docs/estandares/` y `docs/adr/`.

## Mapa

| Ruta | Qué hay |
|---|---|
| `src/main/java/pe/edu/pucp/areasverdes/mod05/` | MOD-05 «Atenciones tercerizadas»: `/api/tercerizados/`, HTTP Basic, JDBC y Flyway |
| `src/main/java/pe/edu/pucp/areasverdes/mod06/` | MOD-06 «Reportes, indicadores e IA»: `/api/v1/`, datos `mock` o `db`, Gemini |
| `src/main/resources/db/migration/` | Migraciones Flyway |
| `src/main/resources/prompts/` | Prompts de la IA |
| `src/test/java/.../apoyo/` | Dobles de prueba y constructores de datos |
| `mock-data/` | Datos simulados (JSON y PDF) para `DATA_SOURCE=mock` |
| `docs/estandares/` | Java, API, base de datos, pruebas y almacenamiento |
| `docs/modulos/` | Documentación funcional por módulo |

## Comandos

```sh
./mvnw spring-boot:run                       # arranca en :8000 (lee .env)
./mvnw verify                                # formato, compilación y pruebas (lo que corre CI)
./mvnw spotless:apply                        # formatea lo que has cambiado
./mvnw test -Dtest=CumplimientoCorteServiceTest   # una clase
```

Las pruebas necesitan PostGIS y las variables `MOD05_TEST_DB_*` (ver `docs/estandares/pruebas.md`). Java 21 exactamente.

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
- Archivos: a través de `ArchivoStorage`, nunca directamente a disco o a S3. En S3, el guard de `S3_PREFIX` es obligatorio.
- Fechas en `America/Lima` y con `java.time`. Importes y medidas en `BigDecimal`.
- Inyección por constructor, DTO como `record`, `@Transactional` en el servicio.
- Nombres: clases en PascalCase, paquetes en minúsculas, claves de configuración propias en kebab-case, variables de entorno en SCREAMING_SNAKE_CASE, migraciones `V<n>__<modulo>_<descripcion>.sql`. El resto en kebab-case.
- Todo en español de España: código de dominio, mensajes, docs y commits.

## Deuda conocida (no la arregles de paso: tiene ticket propio)

- Dos formatos de error (MOD-05 `{codigo, detalle}`; MOD-06 emula DRF en `api/drf`). Objetivo: ProblemDetail (ADR 0004).
- JSON en snake_case. Objetivo: camelCase (ADR 0005).
- Dos configuraciones de seguridad (`mod05/Seguridad` y `mod06/config/SeguridadMod06`) y dos clases `main`.
- Variables con prefijo de módulo (`MOD05_DB_*`) para una base compartida.
- MOD-05 en `/api/tercerizados/`, fuera de `/api/v1/`.
- Archivos en disco (`private-media`). Objetivo: S3.
- `V1__mod05.sql` duplica entidades ajenas (`mod05_usuario`, `mod05_usuario_rol`, `mod05_areaverde`, `mod05_ejemplar`). Objetivo: consolidarlas en las tablas de su dueño.

## Flujo de trabajo

Rama `<feat|fix|chore>/<ID>-<descripcion>`, commit `tipo(ID): descripción`, PR con squash. Al fusionar en `main` se despliega en UAT.
