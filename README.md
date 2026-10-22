# Back-end Áreas Verdes (Spring Boot)

Java 21 y Spring Boot 3.5.16. Un solo servicio en el puerto 8000 (`PORT`).

## Módulos

| Módulo | Paquete | Rutas | Datos |
|---|---|---|---|
| MOD-05 «Atenciones tercerizadas» | `pe.edu.pucp.areasverdes.mod05` | `/api/tercerizados/` (HTTP Basic) | PostgreSQL con Flyway. Ver [README-MOD05.md](README-MOD05.md) |
| MOD-06 «Reportes, indicadores e IA» | `pe.edu.pucp.areasverdes.mod06` | `/api/v1/` (sin autenticación) | `DATA_SOURCE=mock` lee `mock-data/` (`db`, pendiente) |

Documentación de la API: `http://localhost:8000/api/docs` (OpenAPI en `/api/schema`).

## Arranque

PostgreSQL debe estar levantado: el MOD-05 lo necesita para arrancar (ver [README-MOD05.md](README-MOD05.md)).

```sh
cp .env.example .env      # completar MOD05_DB_PASSWORD; GEMINI_API_KEY solo para la IA
./mvnw spring-boot:run    # Windows: .\mvnw.cmd spring-boot:run
```

## Variables (`.env`)

| Variable | Uso |
|---|---|
| `PORT` | Puerto HTTP (8000 por defecto) |
| `DATA_SOURCE` | Fuente del MOD-06: `mock` o `db` |
| `MOCK_DATA_DIR` | Carpeta de datos simulados del MOD-06 (por defecto `../mock-data`) |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | Extracción con IA del MOD-06 (RF-IA-001). La clave no se versiona |
| `GEMINI_THINKING_LEVEL` | Razonamiento de Gemini: `minimal`, `low` (por defecto), `medium` o `high`; vacío lo decide el modelo. Menos razonamiento = respuesta más rápida |
| `IA_FUENTE_REPORTES` | De dónde lee RF-IA-001 el reporte del proveedor y su informe: `mod05` (por defecto, base y archivos reales del MOD-05, solo lectura) o `mock` (`mock-data/`, comportamiento anterior) |
| `POSTGRES_*` | Reservadas para `DATA_SOURCE=db` del MOD-06 (pendiente) |
| `MOD05_*` | Base de datos, archivos y precarga del MOD-05 (ver [README-MOD05.md](README-MOD05.md)) |

## Pruebas

```sh
./mvnw test
```

Corre las pruebas de los dos módulos. Necesitan una base PostgreSQL exclusiva terminada en `_test` (`MOD05_TEST_DB_URL`, `MOD05_TEST_DB_USER`, `MOD05_TEST_DB_PASSWORD`); la suite del MOD-05 vacía sus tablas.

Las pruebas leen `MOD05_TEST_DB_*` de las **variables de entorno**, no de `.env`. Si las tienes en `backend/.env`, cárgalas en la sesión de PowerShell antes de ejecutar (desde `backend/`; no muestra los valores y solo dura lo que la ventana):

```powershell
Get-Content .env | Where-Object { $_ -match '^MOD05_TEST_DB_[A-Z]+=' } | ForEach-Object {
    $nombre, $valor = $_ -split '=', 2
    Set-Item -Path "Env:$nombre" -Value $valor
}
.\mvnw.cmd test
```

Para una sola clase: `.\mvnw.cmd test "-Dtest=FuenteReportesProveedorMod05Test"`. Sin base de prueba, `FuenteReportesProveedorMod05Test` se omite; las pruebas que levantan Spring fallan.
