# Reglas de `main`

`main.json` define el ruleset de la rama principal:

- sin borrado ni force push;
- historia lineal;
- PR obligatorio, sin aprobación mínima: quien abre el PR puede fusionarlo;
- conversaciones resueltas antes de fusionar;
- solo squash;
- check `ci-ok` obligatorio.

Es igual en los tres repos y **está aplicado desde el 28/10/2026**. Para ver el ruleset activo: `gh api repos/unite-z/<repo>/rulesets`.

## Aprobaciones

`required_approving_review_count` está a `0` y `require_code_owner_review` a `false`: cada persona fusiona su propio PR cuando `ci-ok` está en verde. GitHub no deja aprobar el PR propio, así que con `1` nadie podría fusionar sin esperar a otra persona. La revisión ajena sigue siendo recomendable ([git-y-prs.md](https://github.com/unite-z/konoha-infra/blob/main/docs/estandares/git-y-prs.md)).

Para exigir una aprobación más adelante, pon `required_approving_review_count` a `1`. Para exigir también la de `CODEOWNERS` (`require_code_owner_review: true`), cada ruta necesita al menos dos personas: si tiene un único dueño, sus propios PR no se pueden fusionar.

## Requisitos antes de aplicarlo

1. El repo es público, o su dueño tiene un plan de pago: en el plan gratuito, los rulesets no se aplican en repos privados. Los repos de `unite-z` son públicos.
2. CI ha pasado al menos una vez en un PR, para que GitHub conozca el check `ci-ok`.

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
