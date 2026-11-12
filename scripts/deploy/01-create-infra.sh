#!/usr/bin/env bash
# Cria (ou reaproveita) a infraestrutura da API do Vê AI na Azure:
#   Resource Group -> App Service Plan (Linux B1) -> Web App da API (Java 21) + variáveis de ambiente
# NÃO cria banco nem front: a API usa o Oracle da FIAP (perfil fiap) e o app mobile (Expo) consome a API.
# Uso: bash scripts/deploy/01-create-infra.sh
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_vars LOCATION RESOURCE_GROUP APP_SERVICE_PLAN WEB_APP_NAME \
             DB_URL DB_USER DB_PASSWORD JWT_SECRET RABBITMQ_URL OPENAI_API_KEY

echo "==> Assinatura Azure em uso"
require_az
az provider register --namespace Microsoft.Web --wait --output none

echo "==> 1/4 Resource Group $RESOURCE_GROUP ($LOCATION)"
az group create --name "$RESOURCE_GROUP" --location "$LOCATION" --output none

echo "==> 2/4 App Service Plan $APP_SERVICE_PLAN (Linux B1)"
az appservice plan show --resource-group "$RESOURCE_GROUP" --name "$APP_SERVICE_PLAN" --output none 2>/dev/null \
  || az appservice plan create --resource-group "$RESOURCE_GROUP" --name "$APP_SERVICE_PLAN" \
       --location "$LOCATION" --is-linux --sku B1 --output none \
  || { echo "ERRO: falhou em $LOCATION. Se for política/região, use LOCATION=southafricanorth no .env.deploy,"
       echo "      apague o grupo vazio (az group delete -n $RESOURCE_GROUP --yes) e rode de novo."; exit 1; }

echo "==> 3/4 Web App $WEB_APP_NAME (Java 21)"
az webapp show --resource-group "$RESOURCE_GROUP" --name "$WEB_APP_NAME" --output none 2>/dev/null \
  || az webapp create --resource-group "$RESOURCE_GROUP" --plan "$APP_SERVICE_PLAN" --name "$WEB_APP_NAME" \
       --runtime "JAVA:21-java21" --https-only true --output none
az webapp config set --resource-group "$RESOURCE_GROUP" --name "$WEB_APP_NAME" --always-on true --output none
az webapp log config --resource-group "$RESOURCE_GROUP" --name "$WEB_APP_NAME" \
  --application-logging filesystem --docker-container-logging filesystem --level information --output none

echo "==> 4/4 Variáveis de ambiente da API (valores não são exibidos)"
SETTINGS_FILE="$(mktemp)"
trap 'rm -f "$SETTINGS_FILE"' EXIT
chmod 600 "$SETTINGS_FILE"
python3 - "$SETTINGS_FILE" <<'PY'
import json, os, sys
e = os.environ
settings = {
    # Oracle da FIAP (o mesmo schema do .env local)
    "SPRING_PROFILES_ACTIVE": "fiap",
    "DB_URL": e["DB_URL"],
    "DB_USER": e["DB_USER"],
    "DB_PASSWORD": e["DB_PASSWORD"],
    "DB_TIME_ZONE": e.get("DB_TIME_ZONE") or "America/Sao_Paulo",
    "DB_POOL_MAX": e.get("DB_POOL_MAX") or "5",
    # CloudAMQP da entrega de Java (a entrega de DevOps tem outra instância)
    "RABBITMQ_URL": e["RABBITMQ_URL"],
    "JWT_SECRET": e["JWT_SECRET"],
    # IA real na nuvem
    "VEAI_IA_PROVEDOR": "openai",
    "OPENAI_API_KEY": e["OPENAI_API_KEY"],
    "OPENAI_MODEL": e.get("OPENAI_MODEL") or "gpt-6-luna",
    "OPENAI_REASONING_EFFORT": e.get("OPENAI_REASONING_EFFORT") or "none",
    "VEAI_RATE_LIMIT": e.get("VEAI_RATE_LIMIT") or "20",
    # O app mobile (Expo) não precisa de CORS; "*" libera também o Expo Web e o Swagger
    "VEAI_CORS_ORIGENS": e.get("VEAI_CORS_ORIGENS") or "*",
    # O Spring escuta na 8080; o App Service encaminha para ela
    "PORT": "8080",
    "WEBSITES_PORT": "8080",
    "WEBSITES_CONTAINER_START_TIME_LIMIT": "600",
    "JAVA_OPTS": "-XX:MaxRAMPercentage=75 -Duser.timezone=America/Sao_Paulo",
}
for opcional in ("VEAI_RAG_URL", "OPENAI_TEMPERATURE"):
    if e.get(opcional):
        settings[opcional] = e[opcional]
json.dump(settings, open(sys.argv[1], "w"))
PY
az webapp config appsettings set --resource-group "$RESOURCE_GROUP" --name "$WEB_APP_NAME" \
  --settings "@$SETTINGS_FILE" --output none

echo
echo "Infraestrutura pronta."
echo "  API: $API_URL"
echo "Próximo passo: bash scripts/deploy/02-deploy-api.sh"
az resource list --resource-group "$RESOURCE_GROUP" --query "[].{recurso:name, tipo:type}" --output table
