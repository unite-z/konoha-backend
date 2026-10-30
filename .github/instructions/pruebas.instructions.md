---
applyTo: "src/test/**/*.java"
---

# Pruebas del backend (JUnit 5)

- Tipos y nombres:
  - unitaria `<Clase>Test`: reglas, cálculos y validaciones, sin Spring ni base;
  - integración `<Algo>IntegrationTest`: repositorios, SQL, PostGIS, migraciones y transacciones, con PostgreSQL y PostGIS reales (nunca H2);
  - contrato `<Modulo>ApiTest`: rutas, códigos HTTP, JSON y errores con MockMvc.
- Las que levantan Spring terminan en `IntegrationTest` o `ApiTest`, para que el perfil `pruebas-rapidas` las excluya.
- La base de pruebas termina en `_test`: las suites vacían sus tablas.
- Matriz de permisos: cada endpoint tiene su fila con el resultado esperado por rol. Un endpoint nuevo sin fila es un error.
- Las pruebas nunca llaman a Gemini: `ClienteIAFalso` con respuestas grabadas, incluidas las malformadas, el tiempo agotado y la falta de clave (503).
- Nada de `Thread.sleep`: el tiempo se inyecta con `Clock`.
- Datos de prueba con los constructores de `apoyo/Datos`, no con JSON copiados a mano.
- Cada corrección de un bug llega con la prueba que lo reproduce.
- Una prueba intermitente se arregla o se elimina en el mismo día; nada de `@Disabled` sin ticket.
- Lo crítico tiene que estar cubierto: permisos, transiciones de estado, migraciones e idempotencia.
