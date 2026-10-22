#!/usr/bin/env bash
# Crea o actualiza las etiquetas de issues del equipo. Uso: crear-etiquetas.sh <owner/repo>
set -euo pipefail

repo="${1:?Uso: crear-etiquetas.sh <owner/repo>}"

while IFS='|' read -r nombre color descripcion; do
  gh label create "$nombre" --repo "$repo" --color "$color" --description "$descripcion" --force
done <<'ETIQUETAS'
tipo-historia|1d76db|Funcionalidad nueva
tipo-bug|d73a4a|Algo no funciona como debería
tipo-chore|cfd3d7|Mantenimiento, CI, dependencias
mod05|5319e7|Atenciones tercerizadas
mod06|0e8a16|Reportes, indicadores e IA
frontend|fbca04|Interfaz y PWA
infra|006b75|Despliegue e infraestructura
bloqueado|b60205|No puede avanzar hasta resolver otra cosa
necesita-decision|e99695|Falta una decisión del equipo o de la sección
ETIQUETAS
