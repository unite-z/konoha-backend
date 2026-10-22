#!/usr/bin/env bash
# Revisa los ficheros añadidos o renombrados entre BASE y HEAD:
#   1. nombres en kebab-case salvo las excepciones de docs/estandares/nombres.md;
#   2. artefactos que no deben versionarse (builds, dependencias, .env, estado de Terraform, binarios grandes);
#   3. marcas de "no fusionar" en las líneas añadidas.
# Uso: comprobar-higiene.sh <sha-base>
set -euo pipefail

base="${1:?Falta el SHA base}"
errores=0
falla() { echo "::error file=$1::$2"; errores=1; }

kebab='^[a-z0-9]+(-[a-z0-9]+)*(\.[a-z0-9]+)*$'
oculto_kebab='^\.[a-z0-9]+(-[a-z0-9]+)*(\.[a-z0-9]+)*$'
pascal='^[A-Z][A-Za-z0-9]*$'
camel='^[a-z][A-Za-z0-9]*$'
fijos=' README.md CLAUDE.md CONTRIBUTING.md SECURITY.md CODEOWNERS Dockerfile LICENSE .env.example
  docker-compose.yml pull_request_template.md ISSUE_TEMPLATE mvnw mvnw.cmd pom.xml package.json
  package-lock.json vite.config.ts index.html main.tsx App.tsx vite-env.d.ts maven-wrapper.properties
  .terraform.lock.hcl Caddyfile .oxlintrc.json .gitleaks.toml .nvmrc .tflint.hcl '

nombre_valido() {
  local ruta="$1" fichero dir seg sin_ext
  fichero="$(basename "$ruta")"
  dir="$(dirname "$ruta")"

  # Directorios: kebab-case, salvo paquetes Java y carpetas de componente React.
  IFS='/' read -r -a segmentos <<< "$dir"
  for seg in "${segmentos[@]}"; do
    [[ "$seg" == "." ]] && continue
    [[ " $fijos " == *" $seg "* ]] && continue
    if [[ "$ruta" =~ ^src/(main|test)/java/ ]]; then
      [[ "$seg" =~ ^[a-z][a-z0-9]*$ ]] && continue
    fi
    if [[ "$ruta" =~ ^src/ ]] && [[ "$seg" =~ $pascal ]]; then continue; fi
    [[ "$seg" =~ $kebab || "$seg" =~ $oculto_kebab ]] && continue
    return 1
  done

  [[ " $fijos " == *" $fichero "* ]] && return 0
  sin_ext="${fichero%%.*}"

  case "$ruta" in
    src/main/java/*.java|src/test/java/*.java)
      [[ "$sin_ext" =~ $pascal ]] && return 0 || return 1 ;;
    src/main/resources/db/migration/*)
      [[ "$fichero" =~ ^[VR][0-9_]*__[a-z0-9]+(_[a-z0-9]+)*\.sql$ ]] && return 0 || return 1 ;;
    mock-data/*.json)
      [[ "$sin_ext" =~ $pascal || "$fichero" =~ $kebab ]] && return 0 || return 1 ;;
    src/*.tsx|src/*.module.css)
      [[ "$sin_ext" =~ $pascal ]] && return 0 || return 1 ;;
    src/*.ts)
      [[ "$sin_ext" =~ $camel || "$sin_ext" =~ $pascal ]] && return 0 || return 1 ;;
  esac

  [[ "$fichero" =~ $kebab || "$fichero" =~ $oculto_kebab ]]
}

while IFS= read -r ruta; do
  [[ -z "$ruta" ]] && continue
  nombre_valido "$ruta" || falla "$ruta" "Nombre fuera de la regla kebab-case y de sus excepciones (docs/estandares/nombres.md)."

  case "$ruta" in
    .mvn/wrapper/*.jar) ;;
    */target/*|target/*|*/node_modules/*|node_modules/*|dist/*|*/dist/*|*/.terraform/*|.terraform/*)
      falla "$ruta" "Carpeta generada: no se versiona." ;;
    *.class|*.jar|*.war|*.log|*.tfstate|*.tfstate.*|*.zip|*.tar.gz|*.DS_Store)
      falla "$ruta" "Artefacto o fichero de sistema: no se versiona." ;;
    .env|*/.env|*.env|.env.*|*/.env.*)
      [[ "$ruta" == *.env.example ]] || falla "$ruta" "Fichero de entorno con posibles secretos: no se versiona." ;;
  esac

  if [[ -f "$ruta" ]] && (( $(wc -c < "$ruta") > 5242880 )); then
    falla "$ruta" "Más de 5 MB: los binarios grandes no van en el repo."
  fi
done < <(git diff --name-only --diff-filter=AR "$base"...HEAD)

marca="DO_NOT""_MERGE"
if git diff -U0 "$base"...HEAD -- . ':(exclude)*.md' ':(exclude).github/scripts/*' | grep -E '^\+' | grep -q "$marca"; then
  echo "::error::Hay una marca $marca en las líneas añadidas. Quítala antes de fusionar."
  errores=1
fi

if (( errores == 0 )); then echo "Higiene correcta."; fi
exit "$errores"
