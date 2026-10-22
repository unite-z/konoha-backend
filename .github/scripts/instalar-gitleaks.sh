#!/usr/bin/env bash
# Descarga gitleaks en ./.bin y verifica su checksum publicado.
set -euo pipefail

version="8.30.1"
base="https://github.com/gitleaks/gitleaks/releases/download/v${version}"
tar="gitleaks_${version}_linux_x64.tar.gz"

mkdir -p .bin
curl -fsSL -o ".bin/${tar}" "${base}/${tar}"
curl -fsSL -o ".bin/checksums.txt" "${base}/gitleaks_${version}_checksums.txt"
(cd .bin && grep " ${tar}\$" checksums.txt | sha256sum -c -)
tar -xzf ".bin/${tar}" -C .bin gitleaks
.bin/gitleaks version
