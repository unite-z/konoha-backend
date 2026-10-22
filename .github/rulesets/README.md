# Reglas de `main`

`main.json` define el ruleset de la rama principal:

- sin borrado ni force push;
- historia lineal;
- PR obligatorio con una aprobación, revisión de `CODEOWNERS` y conversaciones resueltas;
- solo squash;
- check `ci-ok` obligatorio.

Es igual en los tres repos. **No está aplicado**: se aplica cuando lo apruebe quien coordina el equipo.

## Requisitos antes de aplicarlo

1. La cuenta dueña tiene GitHub Pro (en el plan gratuito, los rulesets no se aplican en repos privados).
2. Hay al menos dos colaboradores con permiso de escritura (nadie puede aprobar su propio PR).
3. `CODEOWNERS` tiene **al menos dos personas por ruta**. Si una ruta tiene un único dueño, sus propios PR no se pueden fusionar. Mientras falten usuarios, aplica el ruleset con `"require_code_owner_review": false`.
4. CI ha pasado al menos una vez en un PR, para que GitHub conozca el check `ci-ok`.

## Aplicarlo

Desde la raíz de cada repo, con `gh` autenticado como administrador:

```sh
repo=<owner>/konoha-backend   # repite con konoha-frontend y konoha-infra; <owner> es la cuenta u organización dueña

# Ajustes de fusión: solo squash, título del PR como mensaje, borrar la rama al fusionar.
gh api -X PATCH "repos/$repo" \
  -F allow_squash_merge=true -F allow_merge_commit=false -F allow_rebase_merge=false \
  -F delete_branch_on_merge=true \
  -f squash_merge_commit_title=PR_TITLE -f squash_merge_commit_message=COMMIT_MESSAGES

# Ruleset de main.
gh api -X POST "repos/$repo/rulesets" --input .github/rulesets/main.json

# Etiquetas de issues.
.github/scripts/crear-etiquetas.sh "$repo"
```

Para comprobarlo: `gh api "repos/$repo/rulesets"`. Para cambiarlo después, edita `main.json` en un PR y aplícalo con `gh api -X PUT "repos/$repo/rulesets/<id>" --input .github/rulesets/main.json`.
