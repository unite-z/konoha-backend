# konoha-backend

API del sistema de gestión de áreas verdes del campus PUCP (DP2). Java 21 y Spring Boot 3.5.16, un solo servicio en el puerto 8000 (`PORT`).

Repos hermanos: `konoha-frontend` (PWA) y `konoha-infra` (despliegue, estándares comunes y ADR).

## Módulos

| Módulo | Paquete | Rutas | Datos |
|---|---|---|---|
| MOD-05 «Atenciones tercerizadas» | `pe.edu.pucp.areasverdes.mod05` | `/api/tercerizados/` (HTTP Basic) | PostgreSQL con Flyway. Ver [docs/modulos/mod05.md](docs/modulos/mod05.md) |
| MOD-06 «Reportes, indicadores e IA» | `pe.edu.pucp.areasverdes.mod06` | `/api/v1/` (sin autenticación) | `DATA_SOURCE=mock` lee `mock-data/` (`db`, pendiente) |

Documentación de la API: `http://localhost:8000/api/docs` (OpenAPI en `/api/schema`).

## Arranque rápido

Requisitos: JDK 21 exactamente y PostgreSQL 14 o posterior (o Docker). Paso a paso para macOS y Windows en [docs/arranque-local.md](docs/arranque-local.md).

```sh
cp .env.example .env      # completar MOD05_DB_PASSWORD; GEMINI_API_KEY solo para la IA
./mvnw spring-boot:run    # Windows: .\mvnw.cmd spring-boot:run
```

## Variables (`.env`)

| Variable | Uso |
|---|---|
| `PORT` | Puerto HTTP (8000 por defecto) |
| `DATA_SOURCE` | Fuente del MOD-06: `mock` o `db` |
| `MOCK_DATA_DIR` | Carpeta de datos simulados del MOD-06 (por defecto `mock-data`) |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | Extracción con IA del MOD-06 (RF-IA-001). La clave no se versiona |
| `GEMINI_THINKING_LEVEL` | Razonamiento de Gemini: `minimal`, `low` (por defecto), `medium` o `high` |
| `IA_FUENTE_REPORTES` | Origen del reporte para RF-IA-001: `mod05` (por defecto) o `mock` |
| `POSTGRES_*` | Reservadas para `DATA_SOURCE=db` del MOD-06 (pendiente) |
| `MOD05_*` | Base de datos, archivos y precarga del MOD-05 (ver [docs/modulos/mod05.md](docs/modulos/mod05.md)) |

## Pruebas

```sh
./mvnw verify
```

Necesitan PostgreSQL con PostGIS en una base terminada en `_test` (`MOD05_TEST_DB_*` como variables de entorno). Cómo levantarla con Docker: [docs/estandares/pruebas.md](docs/estandares/pruebas.md).

## MOD-04: operación de campo

Integración de RF-T-005 (cuadrillas, capataces y operarios), RF-PLA-005 (encargos semanales/quincenales) y RF-ACT-003 (participantes). Sus rutas están bajo `/api/v1/operacion-campo/` y se documentan en Swagger. Requieren HTTP Basic: 401 sin credenciales, 403 sin permiso.

| Operaciones | Jefe de sección | Ingeniero / practicante | Capataz |
| --- | --- | --- | --- |
| Consultar cuadrillas y encargos | Todas | Todas | Solo los suyos |
| Registrar personal, gestionar cuadrillas y encargos | Sí | No | No |
| Consultar actividades y participantes | Todas | Todas | Solo los suyos |
| Abrir parte, registrar actividad y guardar participantes | Sí | No | Solo de su equipo, con parte abierto |

La matriz ejecutable de permisos y la comprobación de cobertura de endpoints están en `Mod04IntegrationTest`. Los alias de ruta con y sin barra final comparten los mismos permisos.

La migración nueva `V5__mod04_operacion_campo.sql` añade exclusivamente tablas del módulo y amplía el catálogo provisional de roles con `Capataz`. Las migraciones V1–V4 se conservan. **Revisar la numeración antes de fusionar**, por si otro PR ocupa V5. Áreas verdes referencia `mod05_areaverde`, la tabla compartida provisional que ya existe; no crea otra copia. Usuarios reutiliza MOD-05 hasta MOD-01. La auditoría específica del módulo es inmutable.

Para la demostración, usar el perfil `dev`, una contraseña propia en `MOD04_DEMO_PASSWORD` y `--mod04.seed=true`. Precarga las cuentas `mod04_jefe` y `mod04_capataz_2`, `_3`, `_4`; es idempotente y no sustituye datos existentes. El catálogo compartido de áreas debe estar disponible: en una base nueva de demostración se puede ejecutar además `--mod05.seed=true` con `MOD05_DEMO_PASSWORD` configurada.

Los POST de partes y actividades aceptan `Idempotency-Key` (UUID). El servidor comprueba usuario, operación y contenido; un reenvío idéntico devuelve el mismo resultado y otro contenido con la misma clave devuelve 409. La vigencia es de 24 horas. El frontend conserva la clave de una actividad al reintentar sin cambiar sus datos. Los PUT reemplazan la composición o selección completa.

Decisiones funcionales conservadas: semanal hasta 7 días y quincenal hasta 14, incluidos ambos extremos; confirmar la interpretación de quincenal con el equipo. Se valida la pertenencia histórica del operario en la fecha de la actividad. El cierre de partes completo y la cola sin conexión de HU-MOD04-012 quedan fuera de estos tres requisitos; la interfaz actual registra con conexión. MOD-06 conserva su fuente de datos existente.

## Documentación

- [AGENTS.md](AGENTS.md): estado, mapa, gates y reglas, para personas y agentes.
- [CONTRIBUTING.md](CONTRIBUTING.md): cómo contribuir.
- [docs/estandares/](docs/estandares/): Java, API, base de datos, pruebas y almacenamiento.
- [docs/base-datos/](docs/base-datos/README.md): modelo entidad-relación implementado (draw.io).
- Estándares comunes y ADR: en `konoha-infra`.
