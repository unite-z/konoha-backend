# Almacenamiento de archivos

## Estado actual y objetivo

- **Hoy**: MOD-05 guarda los archivos en disco (`mod05.archivos`, por defecto `./private-media`).
- **Objetivo**: S3, con la misma interfaz `ArchivoStorage` y dos implementaciones, elegidas con `ALMACENAMIENTO=local|s3`:
  - `local`: disco, para desarrollo;
  - `s3`: UAT y prod.
  La migración va en un ticket propio.

## Reglas para S3

- Un bucket versionado. Cada entorno escribe bajo su prefijo: `S3_PREFIX=uat/` o `S3_PREFIX=prod/`.
- **Guard de prefijo**: la capa de almacenamiento rechaza cualquier clave que no empiece por `S3_PREFIX`. Es la única barrera entre entornos, porque UAT y prod comparten el `LabInstanceProfile` y el rol puede leer y borrar en todo el bucket.
- Formato de clave: `<S3_PREFIX><modulo>/<entidad>/<id>/<uuid>-<nombre-saneado>`. Por ejemplo: `prod/mod05/reporte/42/9f1c...-informe-final.pdf`.
- La base guarda la **clave**, nunca una URL.
- Sin claves de acceso: el SDK toma las credenciales del `LabInstanceProfile` de la EC2.

## Subida y descarga

- **Subida directa desde la PWA** con URL prefirmada de `PUT`:
  1. La PWA pide la URL a la api, que valida permiso, tipo y tamaño.
  2. La PWA sube el archivo directamente a S3.
  3. La PWA confirma a la api, que registra la clave.
- **Descarga** con URL prefirmada de `GET`, tras comprobar el permiso.
- Caducidad de las URL: entre 5 y 15 minutos. Firman con credenciales temporales del rol, así que nunca más.
- Cada reintento de la cola sin conexión pide una URL nueva. Las URL no se guardan.
- Fotos de campo comprimidas en el cliente. La subida multipart solo se usa para los archivos grandes de oficina.
- Tipos admitidos por lista blanca (PDF, JPEG, PNG, XLSX...), comprobados por contenido además de por extensión.

## Entre entornos

- **No se restaura un volcado de prod en UAT sin reescribir las claves.** Las filas restauradas apuntarían a `prod/...`, y un borrado en UAT eliminaría el archivo que prod sigue sirviendo. El guard de prefijo lo impide, pero el procedimiento correcto es sembrar UAT con datos propios o anonimizados.
- El versionado del bucket permite recuperar un borrado accidental. Una regla de ciclo de vida elimina las versiones antiguas a los 30 días.
