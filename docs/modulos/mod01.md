# MOD-01: core, seguridad y trazabilidad

Sesión, usuarios, roles y permisos, auditoría común, adjuntos y repositorio común. Es la base que usan todos los módulos.

| Ticket | Requisito | Qué cubre |
|---|---|---|
| AV-216 | RF-T-001 | Inicio y cierre de sesión, contraseña propia, bloqueo, inactividad |
| AV-217 | RF-T-002 | Alta, edición, activación, desactivación y vigencia de cuentas |
| AV-218 | RF-T-003 | Un único rol por usuario; operario previsto e inactivo |
| AV-219 | RF-T-004 | Permisos por rol, 403 registrado, montos y alcance de cuadrilla |
| AV-222 | RF-T-008 | Historial de altas y modificaciones, inmutable |
| AV-221 | RF-T-007 | Fotografías y documentos de actividades, atenciones, incidencias y ejemplares |
| AV-224 | RF-T-010 | Consulta común de jardines, especies, sectores y servicios |

Código: `src/main/java/pe/edu/pucp/areasverdes/mod01/` (`api`, `servicios`, `datos`, `config`). Tablas `core_`, migraciones `V6` a `V10`.

## Sesión

- Autenticación propia con correo y contraseña (supuesto ACC-S1). Las cuentas copiadas de MOD-05 entran con su nombre de usuario.
- El token de sesión viaja en la cookie `av_sesion` (`HttpOnly`, `Secure`, `SameSite=Strict`, ruta `/api`). La base solo guarda su SHA-256 en `core_sesion`.
- Cada petición revalida la sesión, la cuenta y el rol. Un cambio de rol o una desactivación valen desde la siguiente acción.
- Escrituras: el cliente reenvía la cookie `XSRF-TOKEN` en la cabecera `X-XSRF-TOKEN`. El inicio de sesión está exento.
- Con contraseña temporal, solo se permiten `GET /auth/sesion/`, `POST /auth/contrasena/` y `POST /auth/logout/`. El resto responde 403 `cambio-contrasena-obligatorio`.
- Parámetros (`core.seguridad.*`, supuestos ACC-S4 a ACC-S6, configurables por entorno):

| Clave | Por defecto | Variable |
|---|---|---|
| `intentos-maximos` | 5 | `CORE_INTENTOS_MAXIMOS` |
| `minutos-bloqueo` | 15 | `CORE_MINUTOS_BLOQUEO` |
| `minutos-inactividad` (oficina) | 30 | `CORE_MINUTOS_INACTIVIDAD` |
| `horas-sesion` (duración máxima en oficina) | 12 | — |
| `dias-sesion-persistente` (móvil, «mantener sesión») | 30 | `CORE_DIAS_SESION_PERSISTENTE` |
| `dias-sin-uso` (destacado en la lista) | 60 | `CORE_DIAS_SIN_USO` |
| `dias-aviso-vigencia` | 7 | — |
| `cookie-segura` | `true` | `CORE_COOKIE_SEGURA` |

## Endpoints

Todos bajo `/api/v1/core/`, con y sin barra final. Errores en `{codigo, detalle}`, con campos extra cuando la interfaz los necesita. Listas paginadas de 25 con `count`, `next`, `previous` y `results`.

| Método y ruta | Quién | Notas |
|---|---|---|
| `POST auth/login/` | Público | `{identificador, contrasena, canal: oficina\|campo, mantener_sesion}`. 401 `no-autenticado` con mensaje genérico, `cuenta-bloqueada` o `cuenta-no-activa` |
| `GET auth/sesion/` | Con sesión | Usuario, rol y `permisos`: la interfaz arma el menú con ellos |
| `POST auth/logout/` | Con sesión | 204. Solo cierra la sesión de ese dispositivo |
| `POST auth/contrasena/` | Con sesión | `{contrasena_actual, contrasena_nueva, confirmacion}`. 400 `contrasena-debil` con `reglas` |
| `GET usuarios/` | Jefe | Filtros `rol`, `estado` (`activa`, `inactiva`, `bloqueada`) y `vigencia_proxima`. `destacados`: `sin-uso`, `vence-pronto`, `rechazos` |
| `POST usuarios/` | Jefe | `{correo, nombre, telefono, rol, fecha_fin_vigencia}`. 201 con `contrasena_temporal`, que se muestra una sola vez. 409 `correo-en-uso` (con `cuenta_id` y `puede_reactivar`) o `rol-no-habilitado` |
| `GET` y `PATCH usuarios/{id}/` | Jefe | El PATCH envía los tres campos. `fecha_fin_vigencia: null` quita la vigencia. El correo no cambia |
| `POST usuarios/{id}/desactivar/` | Jefe | `{motivo}`. Cierra todas sus sesiones. 409 `no-autodesactivar`, `ultimo-jefe-seccion` o `encargos-abiertos` |
| `POST usuarios/{id}/activar/` | Jefe | Genera una contraseña temporal |
| `POST usuarios/{id}/desbloquear/` | Jefe | |
| `POST usuarios/{id}/cerrar-sesiones/` | Jefe | Móvil perdido sin desactivar la cuenta |
| `POST usuarios/{id}/contrasena-temporal/` | Jefe | Alternativa a la recuperación por correo |
| `GET usuarios/{id}/historial/` | Jefe | Altas, cambios de datos, de rol y de estado |
| `GET` y `PUT usuarios/{id}/rol/` | Jefe | PUT `{rol, motivo}`. 409 `rol-no-habilitado`, `rol-sin-cambio`, `ultimo-jefe-seccion` o `capataz-con-cuadrilla` |
| `GET roles/` | Jefe | Roles con sus permisos. El operario sale con `activo: false` |
| `GET accesos/` | Jefe | Filtros `usuario_id` y `resultado` (`Exitoso`, `Fallido`, `Bloqueado`, `Denegado`, `SinPermiso`, `CierreSesion`) |
| `GET historial/?entidad=&registro_id=` | Según la entidad | `actividad`, `atencion`, `incidencia`, `ejemplar` y `usuario`. Los importes no salen para quien no tiene `SER.CONTRATO.VER_MONTOS` |
| `POST adjuntos/` | Según la entidad | Multipart `entidad`, `registro_id`, `descripcion` y `archivo`, con la cabecera `Idempotency-Key` (UUID). 201 si es nuevo, 200 si es un reintento, 409 `archivo-duplicado` o `entidad-no-disponible` |
| `GET adjuntos/?entidad=&registro_id=` | Según la entidad | |
| `GET adjuntos/{id}/contenido/` | Según la entidad | Descarga con `Content-Disposition: attachment` |
| `GET repositorio/?tipo=&q=&page=` | Los cuatro roles | `tipo`: `jardin`, `especie`, `sector` o `servicio`. `disponible: false` si la tabla del dueño aún no existe |

La matriz completa por rol está en `src/test/java/.../mod01/Mod01PermisosApiTest.java`. La prueba falla si un endpoint de MOD-01 no está en ella.

Ejemplo de error:

```json
{ "codigo": "correo-en-uso", "detalle": "Ya existe una cuenta inactiva con ese correo. Puedes reactivarla", "cuenta_id": 12, "puede_reactivar": true }
```

## Contrato de autorización para los demás módulos

`servicios/Autorizacion` es lo que los demás módulos usan para comprobar permisos (CU-MOD01-006):

- `exigir(usuario, "MOD.RECURSO.ACCION")`: si falta el permiso, responde 403 «No tiene permiso para esta acción» y registra el intento.
- `puedeVerMontos(usuario)` y `puedeVerDatosPersonales(usuario)`: RNF-008.
- `alcanceCuadrillas(usuario)` y `exigirCuadrilla(usuario, id)`: el capataz solo actúa sobre su cuadrilla. Las cuadrillas las aporta `ConsultaCampo`. Hasta que MOD-04 publique su implementación con `@Primary`, ningún capataz tiene cuadrilla.
- `RegistradorAuditoria.registrarAlta` y `registrarCambio`: el único punto de escritura de la auditoría común. Se llama dentro de la transacción del cambio.

Códigos de permiso (`core_permiso`, matriz fija en esta fase, supuesto ACC-S8). Los marcados con † son sensibles y solo los tiene el jefe de sección.

| Módulo | Códigos |
|---|---|
| MOD-01 | `CORE.USUARIO.GESTIONAR`†, `CORE.CATALOGO.GESTIONAR`, `CORE.CATALOGO.CONSULTAR`, `CORE.REPOSITORIO.CONSULTAR`, `CORE.HISTORIAL.CONSULTAR`, `CORE.PERSONAL.VER_DATOS`† |
| MOD-02 | `INV.INVENTARIO.REGISTRAR`, `INV.INVENTARIO.CONSULTAR`, `INV.PODA.PLANIFICAR`, `INV.PODA.REGISTRAR` |
| MOD-03 | `PLA.PLANIFICACION.GESTIONAR`, `PLA.PLANIFICACION.CONSULTAR`, `PLA.ENCARGO.ACTUALIZAR_ESTADO`, `PLA.CORTE.PLANIFICAR`, `PLA.CORTE.REGISTRAR`, `PLA.INCIDENCIA.REGISTRAR`, `PLA.INCIDENCIA.CLASIFICAR` |
| MOD-04 | `CAM.CUADRILLA.GESTIONAR`†, `CAM.CUADRILLA.CONSULTAR`, `CAM.PARTE.REGISTRAR`, `CAM.PARTE.CONSULTAR`, `CAM.PARTE.VERIFICAR` |
| MOD-05 | `SER.CONTRATO.GESTIONAR`†, `SER.CONTRATO.CONSULTAR`, `SER.CONTRATO.VER_MONTOS`†, `SER.ATENCION.GESTIONAR`, `SER.ATENCION.REGISTRAR`, `SER.CONFORMIDAD.REGISTRAR` |
| MOD-06 | `REP.INDICADOR.CONSULTAR`, `REP.INDICADOR.EXPORTAR`, `REP.MAPA_ATENCIONES.CONSULTAR`, `IA.PROPUESTA.PROPONER`, `IA.PROPUESTA.VALIDAR` |

La asignación por rol sigue la matriz de las Historias de Usuario (sección 2.1) y está en `V9__core_permisos.sql`.

## Cuentas

- `core_usuario` copia `mod05_usuario` con los mismos `id` y hashes:
  - cada cuenta se queda con un solo rol, con prioridad jefe > ingeniero > practicante;
  - las cuentas sin rol entran inactivas.
- Hasta la consolidación (ticket propio), MOD-03 y MOD-05 siguen autenticando con HTTP Basic contra `mod05_usuario`. Una cuenta nueva de MOD-01 todavía no entra en esos módulos.
- Las cuentas de prueba salen con el perfil `dev` y `MOD01_DEMO_PASSWORD`: `jefe@`, `ingeniero@`, `practicante@` y `capataz@pucp.edu.pe`.
- La tarea de vigencia corre cada hora y desactiva las cuentas vencidas. El aviso de 7 días se muestra como destacado `vence-pronto` en la lista (alertas solo dentro de la aplicación, ADR 0003).

## Pendientes

| Tema | Responsable |
|---|---|
| Recuperación por enlace al correo (SES denegado). Hoy, contraseña temporal del jefe | Marcos |
| Decidir entre 60 y 75 días sin uso (CU-007 frente a HU-008) | Nicolás y el equipo de documentación |
| `ConsultaCampo` real (cuadrillas y encargos) al fusionar MOD-04 | Nelson |
| Especies y sectores en el repositorio, y ejemplares en `mod02_ejemplar`, al fusionar MOD-02 | Joel |
| Consolidar `mod05_usuario` en `core_usuario` y pasar MOD-03, 05 y 06 a la sesión de MOD-01 | MOD-01, con cada módulo |
| Adjuntos en S3 con el guard de `S3_PREFIX` | Ticket de almacenamiento |
| Actualizar `docs/base-datos/modelo-er.drawio` con las tablas `core_` | MOD-01 |
