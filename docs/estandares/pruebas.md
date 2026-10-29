# Pruebas del backend

`./mvnw verify` corre todo: formato, compilación y pruebas. Es lo que ejecuta CI.

La verificación técnica local de RF-PLA-001 y RF-PLA-002, sus contratos, fixtures,
resultados y límites frente a UAT están en [MOD-03](../modulos/mod03.md).

## Tipos

| Tipo | Qué cubre | Con qué |
|---|---|---|
| Unitaria (`<Clase>Test`) | Reglas de negocio, cálculos y validaciones | JUnit 5 y dobles de prueba, sin Spring ni base |
| Integración (`<Algo>IntegrationTest`) | Repositorios, SQL, PostGIS, migraciones y transacciones | Spring Boot y **PostgreSQL con PostGIS reales** (nunca H2) |
| Contrato de API (`<Modulo>ApiTest`) | Rutas, códigos HTTP, formato JSON y errores | MockMvc sobre el contexto de Spring |
| Matriz de permisos | Quién puede hacer qué en cada endpoint | Ver abajo |

## Base de datos de pruebas

- Una base exclusiva cuyo nombre termina en `_test`: las suites vacían sus tablas.
- Variables: `MOD05_TEST_DB_URL`, `MOD05_TEST_DB_USER` y `MOD05_TEST_DB_PASSWORD`. Pasan a `TEST_DB_*` cuando se unifique la configuración (deuda con ticket).
- En local, con Docker:

  ```sh
  docker run -d --name konoha-test-db -p 55432:5432 \
    -e POSTGRES_USER=areas_verdes -e POSTGRES_PASSWORD=solo-pruebas \
    -e POSTGRES_DB=mod05_spring_test postgis/postgis:16-3.4
  export MOD05_TEST_DB_URL=jdbc:postgresql://127.0.0.1:55432/mod05_spring_test
  export MOD05_TEST_DB_USER=areas_verdes MOD05_TEST_DB_PASSWORD=solo-pruebas
  ./mvnw verify
  ```

- En Mac con Apple Silicon, usa `imresamu/postgis:16-3.4` (multiarquitectura) en lugar de `postgis/postgis`, que corre emulada.
- En CI, `postgis/postgis:16-3.4` corre como servicio del job.

## Matriz de permisos (gate)

Cada endpoint tiene una fila en la matriz con el resultado esperado por rol: anónimo, Jefe de sección, Ingeniero de campo, Practicante autorizado y los roles que se añadan.

- Una prueba recorre todos los endpoints registrados en Spring y **falla si alguno no está en la matriz**. Añadir un endpoint sin decidir quién puede usarlo rompe CI.
- Otra prueba ejecuta cada combinación de rol y endpoint y compara el código HTTP con el esperado.
- La matriz vive en un único fichero de prueba por módulo, legible como tabla, para que quien revise el PR vea el cambio de permisos de un vistazo.

## IA

- **Las pruebas nunca llaman a Gemini.** Se usa `ClienteIAFalso` con respuestas grabadas.
- Se prueban también las respuestas malformadas, el tiempo agotado y la falta de clave: la api responde 503 y el flujo sigue con validación humana.

## Reglas

- Cada corrección de un bug llega con la prueba que lo reproduce.
- Nada de `Thread.sleep`. El tiempo se inyecta (`Clock`) para probar fechas y plazos.
- Datos de prueba con los constructores de `apoyo/Datos`, no con JSON copiados a mano.
- Una prueba que falla de forma intermitente se arregla o se elimina en el mismo día. No se deja `@Disabled` sin ticket.
- No hay un porcentaje de cobertura obligatorio. Lo crítico tiene que estar cubierto: permisos, transiciones de estado, migraciones e idempotencia.
