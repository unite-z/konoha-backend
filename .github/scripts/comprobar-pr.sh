#!/usr/bin/env bash
# Comprueba el título del PR y el nombre de la rama (docs/estandares/git-y-prs.md).
# Variables: TITULO, RAMA, AUTOR y PREFIJO_TICKET (por defecto AV).
set -euo pipefail

prefijo="${PREFIJO_TICKET:-AV}"
errores=0

if [[ "${AUTOR:-}" == "dependabot[bot]" ]]; then
  echo "PR de Dependabot: no se comprueban título ni rama."
  exit 0
fi

patron_titulo="^(feat|fix|chore|docs|test|refactor)\(${prefijo}-[0-9]+\): [^ ].*[^.]$"
if [[ ! "$TITULO" =~ $patron_titulo ]]; then
  echo "::error::El título del PR debe ser 'tipo(${prefijo}-123): descripción', sin punto final. Recibido: '$TITULO'"
  errores=1
fi
if (( ${#TITULO} > 72 )); then
  echo "::error::El título del PR tiene ${#TITULO} caracteres; el máximo es 72."
  errores=1
fi

patron_rama="^(feat|fix|chore)/${prefijo}-[0-9]+-[a-z0-9]+(-[a-z0-9]+)*$"
if [[ ! "$RAMA" =~ $patron_rama ]]; then
  echo "::error::La rama debe ser '<feat|fix|chore>/${prefijo}-123-descripcion-en-kebab-case'. Recibida: '$RAMA'"
  errores=1
fi

exit "$errores"
