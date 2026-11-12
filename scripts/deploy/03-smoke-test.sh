#!/usr/bin/env bash
# Verificação pós-deploy (só leitura, não grava nada no Oracle):
#   /health UP, Swagger, rota protegida sem token = 401, login da cidadã do seed,
#   componentes db/rabbit/openai UP com o token, GET /regioes e GET /usuarios/me.
# Uso: bash scripts/deploy/03-smoke-test.sh   (BASE_URL=http://... para testar outra URL)
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_vars SMOKE_EMAIL SMOKE_SENHA
BASE_URL="${BASE_URL:-$API_URL}"
[ -n "$BASE_URL" ] || { echo "ERRO: defina WEB_APP_NAME no .env.deploy (ou BASE_URL)"; exit 1; }
FAIL=0
check() { # descrição, condição (0 = ok)
  if [ "$2" = "0" ]; then echo "  OK    $1"; else echo "  FALHA $1"; FAIL=1; fi
}
json() { python3 -c "import json,sys
try:
    d = json.load(sys.stdin); print(eval('d' + sys.argv[1]))
except Exception: print('')" "$1"; }

echo "API: $BASE_URL"
BODY=$(curl -s --max-time 30 "$BASE_URL/health")
check "GET /health = {\"status\":\"UP\"}" "$(health_up "$BODY" && echo 0 || echo 1)"

CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 "$BASE_URL/swagger-ui/index.html")
check "Swagger publicado (HTTP $CODE)" "$([ "$CODE" = 200 ] && echo 0 || echo 1)"

CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 30 "$BASE_URL/relatos/meus")
check "GET /relatos/meus sem token = 401 (HTTP $CODE)" "$([ "$CODE" = 401 ] && echo 0 || echo 1)"

LOGIN=$(SMOKE_EMAIL="$SMOKE_EMAIL" SMOKE_SENHA="$SMOKE_SENHA" python3 -c \
  'import json,os;print(json.dumps({"email":os.environ["SMOKE_EMAIL"],"senha":os.environ["SMOKE_SENHA"]}))')
TOKEN=$(curl -s --max-time 30 -X POST "$BASE_URL/auth/login" -H 'Content-Type: application/json' -d "$LOGIN" | json "['token']")
check "POST /auth/login ($SMOKE_EMAIL) devolve token" "$([ -n "$TOKEN" ] && echo 0 || echo 1)"

if [ -n "$TOKEN" ]; then
  HEALTH=$(curl -s --max-time 30 "$BASE_URL/health" -H "Authorization: Bearer $TOKEN")
  for componente in db rabbit openai; do
    STATUS=$(printf '%s' "$HEALTH" | json "['components']['$componente']['status']")
    check "componente $componente = UP ($STATUS)" "$([ "$STATUS" = UP ] && echo 0 || echo 1)"
  done
  REGIAO=$(curl -s --max-time 30 "$BASE_URL/regioes" -H "Authorization: Bearer $TOKEN" | json "[0]['nome']")
  check "GET /regioes lê o Oracle (1ª região: $REGIAO)" "$([ -n "$REGIAO" ] && echo 0 || echo 1)"
  EMAIL=$(curl -s --max-time 30 "$BASE_URL/usuarios/me" -H "Authorization: Bearer $TOKEN" | json "['email']")
  check "GET /usuarios/me é a conta do login" "$([ "$EMAIL" = "$SMOKE_EMAIL" ] && echo 0 || echo 1)"
fi

echo
if [ "$FAIL" = 0 ]; then echo "Tudo certo."; else echo "Há falhas acima."; exit 1; fi
