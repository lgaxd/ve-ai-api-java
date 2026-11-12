#!/usr/bin/env bash
# Funções comuns dos scripts de deploy (use com: source "$(dirname "${BASH_SOURCE[0]}")/lib.sh").
# Lê o scripts/deploy/.env.deploy e o .env da API SEM executá-los como shell,
# então valores com espaços, '&' ou '$' (ex.: senhas) chegam intactos.

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$DEPLOY_DIR/../.." && pwd)"

load_env_file() {
  local file="$1" line key value
  [ -f "$file" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in ''|\#*) continue ;; esac
    key="${line%%=*}"
    value="${line#*=}"
    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    # Aspas externas opcionais
    if [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]]; then value="${BASH_REMATCH[1]}"; fi
    # Variáveis já definidas no ambiente têm prioridade
    [ -n "${!key+x}" ] || export "$key=$value"
  done < "$file"
}

load_env_file "$DEPLOY_DIR/.env.deploy"
load_env_file "$PROJECT_DIR/.env"

# Usada pelos scripts que fazem source deste arquivo
export API_URL=""
[ -n "${WEB_APP_NAME:-}" ] && API_URL="https://${WEB_APP_NAME}.azurewebsites.net"

require_vars() {
  local v
  for v in "$@"; do
    [ -n "${!v:-}" ] || { echo "ERRO: defina $v em scripts/deploy/.env.deploy (ou no .env)"; exit 1; }
  done
}

require_az() {
  command -v az >/dev/null || { echo "ERRO: Azure CLI (az) não encontrada."; exit 1; }
  az account show --query "{assinatura:name, id:id}" --output table \
    || { echo "ERRO: faça 'az login' antes."; exit 1; }
}

# Corpo do /health com status UP? (enquanto o Spring não sobe, o App Service responde a página "Welcome")
health_up() { printf '%s' "$1" | grep -q '"status":"UP"'; }
