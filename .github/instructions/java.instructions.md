---
applyTo: "src/main/java/**/*.java"
---

# Java, Spring y API (konoha-backend)

## Paquetes y nombres

- `pe.edu.pucp.areasverdes.comun/` para lo compartido; cada módulo en `modNN/` con `api/` (controladores y DTO), `servicios/` (reglas y transacciones), `datos/` (repositorios JDBC) y `config/`. MOD-03 y MOD-05 aún son planos: se ordenan en tickets propios, no de paso.
- Un módulo no importa clases internas de otro: lo compartido sube a `comun/` o se expone con un servicio público.
- Dominio en español y sufijo técnico de Spring en inglés: `AtencionController`, `AtencionService`. Métodos con verbo: `registrarInicio`. Constantes en MAYÚSCULAS.
- Sin imports con comodín. Una clase pública por fichero.

## Spring

- Inyección por constructor con campos `final`. Nada de `@Autowired` en campos.
- DTO como `record` con componentes en camelCase y Bean Validation. El JSON sale en snake_case por la configuración global de Jackson: nada de `@JsonProperty` para renombrar.
- Los controladores validan con `@Valid` y no contienen reglas de negocio. `@Transactional` en el servicio.
- Configuración con `@ConfigurationProperties`, claves propias en kebab-case.
- Los errores de negocio son excepciones propias que traduce el manejador global. Un servicio nunca devuelve un `ResponseEntity` de error.
- `@Scheduled` idempotente y con marca de última ejecución en la base.

## SQL (Spring JDBC)

- Siempre `NamedParameterJdbcTemplate` con parámetros. Columnas de orden o filtro dinámicas: lista blanca en el código.
- Consultas largas en bloques de texto (`"""`).

## Tipos y logs

- `LocalDate` para días y `OffsetDateTime` para instantes; zona `America/Lima` siempre explícita. Importes y medidas en `BigDecimal`, nunca `double`.
- `Optional` en búsquedas que pueden no encontrar nada; las colecciones nunca son `null`.
- SLF4J con placeholders. Sin datos personales ni secretos. `error` solo para fallos que requieren acción.
- Javadoc en clases y métodos públicos de servicios y controladores (alimenta OpenAPI).

## API

- Rutas `/api/v1/<modulo>/` en kebab-case, recursos en plural. Las acciones de estado son subrecursos con `POST` (`/atenciones/{id}/inicio/`).
- Códigos: 201 con `Location` al crear, 400 validación, 401 sin credenciales, 403 sin permiso, 404, 409 conflicto de estado, 503 dependencia caída (S3, Gemini).
- Fechas `AAAA-MM-DD`, instantes ISO 8601 con zona, importes como texto decimal, geometrías en GeoJSON WGS84.
- Paginación `{count, next, previous, results}` con `?page=N` y tamaño 25. Filtros en snake_case; orden con `?orden=campo`, en lista blanca.
- Endpoints de la cola sin conexión: idempotentes con `Idempotency-Key` (misma clave y cuerpo: respuesta guardada; cuerpo distinto: 409).
- Cada endpoint con `@Operation(summary = ...)`, sus errores y su esquema de seguridad, y con su fila en la matriz de permisos.
