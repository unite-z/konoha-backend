#!/usr/bin/env bash
# Convenciones de código Java que solo se exigen en las líneas añadidas del PR (AV-246):
#   1. una clase con @SpringBootTest debe terminar en IntegrationTest o ApiTest: el perfil pruebas-rapidas
#      del pom.xml excluye solo *IntegrationTest y *ApiTest, y cualquier otra levantaría Spring en la tanda rápida.
#      Las clases abstractas (bases de pruebas) no se ejecutan y se admiten;
#   2. @JsonProperty: el snake_case lo aplica la configuración global de Jackson (ADR 0005, docs/estandares/api.md).
#      Un caso justificado se marca con el comentario json-property-ok.
# Uso: comprobar-codigo-nuevo.sh <sha-base>
set -euo pipefail

base="${1:?Falta el SHA base}"
errores=0
falla() { echo "::error file=$1,line=$2::$3"; errores=1; }

# Líneas añadidas, con su número en el fichero nuevo (a partir de las cabeceras @@ del diff).
while IFS=$'\t' read -r fichero linea texto; do
  [[ "$texto" =~ ^[[:space:]]*(//|\*|/\*) ]] && continue
  if [[ "$texto" == *@SpringBootTest* && "$fichero" == src/test/java/* && -f "$fichero" ]]; then
    clase="$(basename "$fichero" .java)"
    if [[ ! "$clase" =~ (IntegrationTest|ApiTest)$ ]] && ! grep -qE "^[[:space:]]*(public[[:space:]]+)?abstract[[:space:]]+class[[:space:]]+$clase\b" "$fichero"; then
      falla "$fichero" "$linea" "La clase $clase usa @SpringBootTest y no termina en IntegrationTest ni en ApiTest: el perfil pruebas-rapidas no la excluye. Renómbrala a ${clase%Test}IntegrationTest (repositorios, SQL) o ${clase%Test}ApiTest (contrato HTTP), docs/estandares/pruebas.md."
    fi
  fi
  if [[ "$texto" == *@JsonProperty* && "$texto" != *json-property-ok* ]]; then
    falla "$fichero" "$linea" "@JsonProperty en código nuevo: la configuración global de Jackson ya convierte a snake_case (ADR 0005, docs/estandares/api.md). Quítalo; si es imprescindible, márcalo con el comentario json-property-ok."
  fi
done < <(git diff -U0 --diff-filter=AM "$base"...HEAD -- 'src/*.java' \
  | awk '/^\+\+\+ b\// { f = substr($0, 7); next }
         /^@@/ { split($3, a, ","); n = substr(a[1], 2) + 0; next }
         /^\+/ { t = substr($0, 2); if (t ~ /@SpringBootTest|@JsonProperty/) print f "\t" n "\t" t; n++ }')

if (( errores == 0 )); then echo "Código nuevo correcto."; fi
exit "$errores"
