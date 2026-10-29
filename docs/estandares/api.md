# Convenciones de la API

## Rutas

- Prefijo **`/api/v1/<modulo>/`**, con el módulo en kebab-case: `/api/v1/tercerizados/`, `/api/v1/reportes/`.
- Recursos en plural y kebab-case: `/atenciones/{id}/orden-compra/`.
- Las acciones que cambian el estado son subrecursos con `POST`: `/atenciones/{id}/inicio/`.
- La forma canónica lleva barra final (`/atenciones/`). También se acepta sin ella.
- Estado actual (deuda con ticket propio):
  - MOD-05 está en `/api/tercerizados/` y pasa a `/api/v1/tercerizados/`.
  - MOD-06 está en `/api/v1/...`, sin segmento de módulo, y pasa a `/api/v1/reportes/...`.
  - Durante la transición se sirven ambas rutas.

| Método | Uso | Éxito |
|---|---|---|
| `GET` | Leer | 200 |
| `POST` | Crear o ejecutar una acción | 201 con `Location` al crear; 200 en las acciones |
| `PATCH` | Modificar parte de un recurso | 200 |
| `DELETE` | Borrar (poco frecuente; casi todo es baja lógica) | 204 |

## Errores

Formato objetivo para el código nuevo: **ProblemDetail (RFC 9457)**, con `codigo` estable en kebab-case y `errores` por campo en la validación. Ver ADR 0004 (`konoha-infra/docs/adr/`) (estado: propuesto).

| Código | Cuándo |
|---|---|
| 400 | Validación o formato incorrecto |
| 401 | Sin credenciales o credenciales inválidas |
| 403 | Autenticado, pero sin permiso |
| 404 | El recurso no existe o no es visible para ese usuario |
| 409 | Conflicto de estado: transición no permitida, versión desactualizada |
| 413 | Archivo demasiado grande |
| 415 | Tipo de archivo no admitido |
| 503 | Dependencia caída: S3, Gemini |

Deuda: MOD-05 y MOD-06 usan `{codigo, detalle}` y formatos heredados de DRF. Se migra en tickets aparte.

## Formato JSON

- **Claves en snake_case, obligatorio** en entrada y salida (ADR 0005 en `konoha-infra/docs/adr/`). Lo aplica la configuración global de Jackson (`spring.jackson.property-naming-strategy: SNAKE_CASE`): los campos Java van en camelCase y no hace falta `@JsonProperty`.
- Fechas `AAAA-MM-DD`. Instantes en ISO 8601 con zona (`2026-10-22T09:30:00-05:00`). Las reglas de calendario van en `America/Lima`.
- Importes y metrados como texto decimal (`"12.50"`), para no perder precisión.
- Los valores enumerados son estables y están documentados en OpenAPI. Se puede cambiar su etiqueta en pantalla, nunca su valor.
- Geometrías en GeoJSON, en WGS84 (EPSG:4326), con orden `[longitud, latitud]`.

## Paginación

```json
{ "count": 132, "next": "/api/v1/tercerizados/atenciones/?page=3", "previous": "/api/v1/tercerizados/atenciones/?page=1", "results": [ ... ] }
```

- `?page=N`, empezando en 1. Tamaño fijo de 25, que es lo que espera el frontend.
- Filtros como parámetros de consulta en snake_case (`?area_verde_id=3`). Para ordenar: `?orden=campo` o `?orden=-campo`, con lista blanca en el servidor.

## Idempotencia

Obligatoria en los endpoints que usa la cola sin conexión de la PWA (creaciones y acciones de campo).

- El cliente genera un UUID por operación y lo envía en la cabecera `Idempotency-Key`. En los reintentos se usa el mismo valor.
- El servidor guarda durante 24 h la clave, el usuario, un hash del cuerpo y la respuesta:
  - misma clave y mismo cuerpo: devuelve la respuesta guardada, sin repetir la operación;
  - misma clave y cuerpo distinto: responde 409 con `codigo: idempotency-key-reutilizada`.
- Si el recurso tiene identificador generado en el cliente (UUID), el `POST` es idempotente por ese identificador, igual que con la cabecera.

## Autenticación

- Sesión de MOD-01: cookie `HttpOnly` `av_sesion` y, en las escrituras, el token de la cookie `XSRF-TOKEN` en la cabecera `X-XSRF-TOKEN`. La aceptan MOD-01, MOD-03 y MOD-05 (ver `docs/modulos/mod01.md`).
- MOD-03 y MOD-05 aceptan además HTTP Basic con los usuarios de MOD-05, solo para pruebas y el smoke test del despliegue. Se retira con la consolidación de usuarios.
- Cada endpoint declara su regla de acceso de forma explícita y entra en la matriz de permisos (ver `pruebas.md`).

## OpenAPI

- springdoc publica `/api/docs` (Swagger UI) y `/api/schema`.
- Cada endpoint lleva `@Operation(summary = ...)`, las respuestas de error posibles y el esquema de seguridad.
- Un cambio incompatible de contrato (quitar un campo, cambiar un tipo o un significado) necesita:
  - un PR coordinado con el frontend;
  - un periodo en el que se sirven el formato viejo y el nuevo;
  - o una ruta nueva.
  Añadir campos opcionales no rompe nada.

## Archivos

Las subidas y descargas usan URL prefirmadas de S3. Ver `almacenamiento.md`.
