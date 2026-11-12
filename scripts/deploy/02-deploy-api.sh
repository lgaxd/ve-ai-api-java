#!/usr/bin/env bash
# Compila (se preciso) e publica o JAR da API no App Service, esperando o /health ficar UP.
# Uso: bash scripts/deploy/02-deploy-api.sh [caminho/do/arquivo.jar]
#   Sem argumento: recompila com Maven (JDK 21) e envia o JAR de target/.
#   SKIP_BUILD=1 envia o JAR que já está em target/ sem recompilar.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_vars RESOURCE_GROUP WEB_APP_NAME

if [ -z "${1:-}" ] && [ "${SKIP_BUILD:-0}" != "1" ]; then
  VERSAO_JAVA="$(java -version 2>&1 | awk -F'"' '/version/ {print $2}' | cut -d. -f1)"
  [ "${VERSAO_JAVA:-0}" -ge 21 ] 2>/dev/null \
    || { echo "ERRO: o build precisa do JDK 21 (encontrado: ${VERSAO_JAVA:-nenhum}). Rode no WSL ou ajuste o JAVA_HOME."; exit 1; }
  echo "==> 0/2 Compilando (os testes rodam no 'mvnw verify'; aqui -DskipTests)"
  (cd "$PROJECT_DIR" && bash ./mvnw -B -q clean package -DskipTests)
fi
JAR="${1:-}"
if [ -z "$JAR" ]; then
  for candidato in "$PROJECT_DIR"/target/ve-ai-api-java-*.jar; do JAR="$candidato"; break; done
fi
[ -f "$JAR" ] || { echo "ERRO: JAR não encontrado: $JAR"; exit 1; }

echo "==> 1/2 Enviando $(basename "$JAR") para $WEB_APP_NAME"
az webapp deploy --resource-group "$RESOURCE_GROUP" --name "$WEB_APP_NAME" \
  --src-path "$JAR" --type jar --track-status false --output none

echo "==> 2/2 Aguardando $API_URL/health responder UP (até 10 minutos)"
# O "az webapp deploy" volta antes do reinício, e a instância antiga ainda responderia UP.
# Por isso: espera alguns segundos e só aceita 3 respostas UP seguidas.
sleep 30
SEGUIDAS=0
for i in $(seq 1 60); do
  BODY=$(curl -s --max-time 15 "$API_URL/health" || true)
  if health_up "$BODY"; then
    SEGUIDAS=$((SEGUIDAS + 1))
    if [ "$SEGUIDAS" -ge 3 ]; then
      echo "API saudável (aplicação + Oracle + RabbitMQ + OpenAI): $API_URL"
      echo "Swagger: $API_URL/swagger-ui.html"
      exit 0
    fi
    sleep 5; continue
  fi
  SEGUIDAS=0
  case "$BODY" in
    *'"status":"DOWN"'*|*'"status":"OUT_OF_SERVICE"'*)
      echo "  tentativa $i/60: aplicação no ar, mas algum componente fora (Oracle, RabbitMQ ou OpenAI)";;
    *"<html"*|*"<!DOCTYPE"*) echo "  tentativa $i/60: ainda iniciando (página padrão do App Service)";;
    *) echo "  tentativa $i/60: sem resposta";;
  esac
  sleep 10
done

echo "ERRO: a API não ficou saudável."
echo "  DOWN = aplicação no ar, mas sem acesso ao Oracle da FIAP, ao CloudAMQP ou à OpenAI."
echo "        Detalhes: faça login no Swagger e abra /health com o token (mostra cada componente)."
echo "  página padrão/sem resposta = ainda iniciando ou falha ao subir."
echo "Logs: az webapp log tail --resource-group $RESOURCE_GROUP --name $WEB_APP_NAME"
exit 1
