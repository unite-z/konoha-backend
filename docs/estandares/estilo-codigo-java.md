# Estilo de código Java y Spring

## Formato

- **google-java-format, estilo AOSP** (sangría de 4 espacios, como MOD-06), aplicado con Spotless.
- `ratchetFrom origin/main`: Spotless solo formatea y comprueba los ficheros que cambias respecto a `main`. El código antiguo se formatea cuando alguien lo toca, no en un PR masivo.

```sh
./mvnw spotless:apply    # formatea lo tocado
./mvnw spotless:check    # lo que corre CI
```

- Sin imports con comodín (`import java.util.*`). Spotless elimina los imports sin usar.
- Una clase pública por fichero.

## Paquetes

```
pe.edu.pucp.areasverdes
├── comun/                  # lo compartido entre módulos: errores, seguridad, configuración
└── modNN/
    ├── api/                # controladores y DTO de entrada y salida
    ├── servicios/          # reglas de negocio y transacciones
    ├── datos/              # repositorios JDBC
    ├── config/             # @ConfigurationProperties y beans del módulo
    └── ...                 # subpaquetes de dominio si hacen falta (ia/, permisos/)
```

- MOD-06 ya sigue esta forma. MOD-05 es plano: se ordena cuando se toque, en tickets propios.
- Un módulo no importa clases internas de otro. Lo compartido sube a `comun/` o se expone por un servicio público del módulo.

## Nombres

- Dominio en español, sufijo técnico de Spring en inglés: `AtencionController`, `AtencionService`, `Mod05Repository`.
- Clases en PascalCase, métodos y campos en camelCase, constantes en `MAYUSCULAS`, paquetes en minúsculas sin guiones. Ver `nombres.md` en `konoha-infra`.
- Métodos con verbo: `registrarInicio`, `buscarPorSector`.

## Spring

- **Inyección por constructor**, campos `final`. Nada de `@Autowired` en campos.
- DTO como `record` con componentes en camelCase y Bean Validation (`@NotNull`, `@Size`...). El JSON sale en snake_case por la configuración global de Jackson. Los controladores validan con `@Valid` y no contienen reglas de negocio.
- `@Transactional` en el servicio, no en el controlador ni en el repositorio.
- Configuración con `@ConfigurationProperties`. Claves propias en kebab-case (`areas-verdes.almacenamiento.prefijo`), alimentadas por variables de entorno en SCREAMING_SNAKE_CASE.
- Los errores de negocio son excepciones propias que el manejador global traduce a HTTP (ver `api.md`). Nunca se devuelve un `ResponseEntity` de error a mano desde un servicio.
- Tareas programadas con `@Scheduled`, idempotentes y con marca de última ejecución en la base (la api puede estar apagada a la hora prevista).

## SQL con Spring JDBC

- Siempre con parámetros (`NamedParameterJdbcTemplate`). **Nunca se concatena una entrada del usuario en un SQL.**
- Columnas ordenables o filtrables de forma dinámica: lista blanca de nombres en el código.
- Consultas largas en bloques de texto (`"""`), con el SQL legible.

## Tipos

- Fechas con `java.time`: `LocalDate` para días y `OffsetDateTime` para instantes. La zona de negocio es `America/Lima`, siempre explícita.
- Importes, metrados y medidas: `BigDecimal`, nunca `double`.
- Se devuelve `Optional` en búsquedas que pueden no encontrar nada. Las colecciones nunca son `null`.

## Logs

- SLF4J con placeholders: `log.info("Atención {} finalizada", id)`.
- Sin datos personales, contraseñas, tokens ni cuerpos completos de peticiones.
- `warn` para lo que alguien debe mirar y `error` solo para fallos que requieren acción.

## Comentarios

- Explican el porqué y las reglas del dominio (con el requisito, por ejemplo `RF-SER-005`), no lo que el código ya dice.
- Javadoc en clases y métodos públicos de servicios y controladores. Esos textos alimentan OpenAPI.
