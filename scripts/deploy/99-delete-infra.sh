#!/usr/bin/env bash
# APAGA o Resource Group da API (plano e Web App). O Oracle da FIAP e o CloudAMQP não são afetados.
# Use só depois da entrega e da nota. Uso: bash scripts/deploy/99-delete-infra.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_vars RESOURCE_GROUP
require_az
read -r -p "Isto apaga TUDO em '$RESOURCE_GROUP'. Digite o nome do Resource Group para confirmar: " CONFIRMACAO
if [ "$CONFIRMACAO" != "$RESOURCE_GROUP" ]; then
  echo "Nome diferente. Nada foi apagado."
  exit 1
fi

az group delete --name "$RESOURCE_GROUP" --yes --no-wait
echo "Exclusão de '$RESOURCE_GROUP' iniciada (leva alguns minutos)."
