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

## Documentación

- [CLAUDE.md](CLAUDE.md): mapa del repo y reglas, para personas y agentes.
- [CONTRIBUTING.md](CONTRIBUTING.md): cómo contribuir.
- [docs/estandares/](docs/estandares/): Java, API, base de datos, pruebas y almacenamiento.
- Estándares comunes y ADR: en `konoha-infra`.
