#!/usr/bin/env bash
# Sobe a API do Vê AI do zero (infra + deploy + verificação).
# Pensado para o ciclo "derruba com o 99 / sobe de novo quando precisar".
# Se uma exclusão anterior ainda estiver em andamento, espera ela terminar
# (os nomes ficam presos enquanto o resource group está em 'Deleting').
# Uso: bash scripts/deploy/up.sh      (~10 minutos)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_vars RESOURCE_GROUP
require_az

echo "==> Verificando se o resource group $RESOURCE_GROUP ainda está sendo apagado"
STATE=""
for i in $(seq 1 60); do
  STATE=$(az group show --name "$RESOURCE_GROUP" --query properties.provisioningState --output tsv 2>/dev/null || true)
  [ "$STATE" = "Deleting" ] || break
  echo "  Deleting... ($i/60)"
  sleep 20
done
[ "$STATE" = "Deleting" ] && { echo "ERRO: a exclusão não terminou em 20 minutos."; exit 1; }

D="$(dirname "${BASH_SOURCE[0]}")"
bash "$D/01-create-infra.sh"
bash "$D/02-deploy-api.sh"
bash "$D/03-smoke-test.sh"
