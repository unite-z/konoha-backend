# Cómo contribuir al backend

Las normas comunes (ticket, rama, commit, PR, calendario) están en `CONTRIBUTING.md` y `docs/estandares/git-y-prs.md` de `konoha-infra`. Aquí solo va lo específico del backend.

## Una vez por clon

```sh
git config core.hooksPath .githooks
cp .env.example .env
```

## Antes de abrir el PR

```sh
./mvnw spotless:apply
./mvnw verify
```

- `verify` necesita PostGIS (ver [docs/estandares/pruebas.md](docs/estandares/pruebas.md)).
- Si añades un endpoint: matriz de permisos, OpenAPI y prueba de contrato.
- Si añades una migración: el siguiente `V<n>` libre, expand/contract, y explícalo en el PR.
- Si cambias el contrato JSON: avisa en el PR del frontend que lo consume.

## Formato pendiente

Spotless formatea el fichero entero en cuanto alguien lo toca (`ratchetFrom`). Está previsto un PR único que formatee todo el código de una vez, coordinado para cuando no haya ramas abiertas. Hasta entonces:

- si tocas un fichero antiguo, el formato del fichero entero va en un commit aparte (`style(AV-n): aplica formato`), para que la revisión vea por separado el cambio real;
- no formatees ficheros que no tocas.

## Checklist de revisión

- [ ] Hay pruebas y cubren el caso que motiva el cambio.
- [ ] Los permisos están en la matriz.
- [ ] Ningún SQL concatena entradas.
- [ ] Las migraciones son compatibles con la versión anterior.
- [ ] No se registran datos personales ni secretos en logs.
- [ ] La documentación está actualizada si cambia el comportamiento.
