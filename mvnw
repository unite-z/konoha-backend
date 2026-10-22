#!/bin/sh
# Maven Wrapper mínimo (macOS y Linux): permite compilar y ejecutar sin instalar Maven; solo requiere JDK 21.
# La primera vez descarga la distribución indicada en .mvn/wrapper/maven-wrapper.properties, comprueba su
# SHA-512 publicado y la guarda en ~/.m2/wrapper/dists. Uso igual que mvn: ./mvnw spring-boot:run
set -eu

BASEDIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPS="$BASEDIR/.mvn/wrapper/maven-wrapper.properties"
[ -f "$PROPS" ] || { echo "mvnw: falta $PROPS" >&2; exit 1; }

URL=$(sed -n 's/^distributionUrl=//p' "$PROPS" | tr -d '\r')
[ -n "$URL" ] || { echo "mvnw: distributionUrl vacío en $PROPS" >&2; exit 1; }
NOMBRE=$(basename "$URL" -bin.zip)
DESTINO="${MAVEN_USER_HOME:-$HOME/.m2}/wrapper/dists/$NOMBRE"

descargar() {
  if command -v curl >/dev/null 2>&1; then curl -fsSL -o "$2" "$1"
  elif command -v wget >/dev/null 2>&1; then wget -q -O "$2" "$1"
  else echo "mvnw: se necesita curl o wget" >&2; return 1
  fi
}

sha512() {
  if command -v shasum >/dev/null 2>&1; then shasum -a 512 "$1" | cut -d' ' -f1
  else sha512sum "$1" | cut -d' ' -f1
  fi
}

if [ ! -x "$DESTINO/bin/mvn" ]; then
  echo "mvnw: descargando $NOMBRE (solo la primera vez)…" >&2
  TMP=$(mktemp -d)
  trap 'rm -rf "$TMP"' EXIT INT TERM
  descargar "$URL" "$TMP/maven.zip"
  descargar "$URL.sha512" "$TMP/maven.zip.sha512"
  ESPERADO=$(cut -d' ' -f1 < "$TMP/maven.zip.sha512" | tr -d '\r\n')
  OBTENIDO=$(sha512 "$TMP/maven.zip")
  if [ "$ESPERADO" != "$OBTENIDO" ]; then
    echo "mvnw: la suma SHA-512 de $NOMBRE no coincide; descarga descartada" >&2
    exit 1
  fi
  unzip -q "$TMP/maven.zip" -d "$TMP"
  mkdir -p "$(dirname "$DESTINO")"
  rm -rf "$DESTINO"
  mv "$TMP/$NOMBRE" "$DESTINO"
  rm -rf "$TMP"
  trap - EXIT INT TERM
fi

exec "$DESTINO/bin/mvn" "$@"
