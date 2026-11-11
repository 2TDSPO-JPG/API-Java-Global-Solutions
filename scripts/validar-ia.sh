#!/usr/bin/env bash
# Validação do Spring AI (Gemini) no fluxo da operadora: POST /sensors/{id}/insights.
# Com GEMINI_API_KEY na API, espera aiGenerated=true; sem chave ou com o provedor fora, aiGenerated=false
# (resumo calculado). Os números (indicators) vêm sempre do backend.
#   GEMINI_API_KEY=... ./mvnw spring-boot:run      (em outro terminal)
#   bash scripts/validar-ia.sh                      (ESPERADO=true por padrão; use ESPERADO=false para o fallback)
set -euo pipefail
API_URL="${API_URL:-http://localhost:8080/api}"
DEVICE_KEY="${DEVICE_KEY:-local-device-key}"
SENHA="${SENHA:-Saneia2026!}"
ESPERADO="${ESPERADO:-true}"
C=(curl --noproxy '*' -sS)
json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
login() { "${C[@]}" -X POST "$API_URL/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$1\",\"password\":\"$SENHA\"}" | json 'd["token"]'; }

ADMIN=$(login "${ADMIN_EMAIL:-admin@saneia.test}"); OPER=$(login "${OPERATOR_EMAIL:-operadora@saneia.test}")
PREFIXO="IA-$(date +%H%M%S)"
SENSOR=$("${C[@]}" -X POST "$API_URL/admin/sensors" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d "{\"prefix\":\"$PREFIXO\",\"name\":\"Validação IA\",\"equipmentType\":\"Reservatório com sensor de nível\",\"address\":\"Rua da IA, 1\",\"neighborhood\":\"Centro\",\"coordinate\":{\"latitude\":-23.97,\"longitude\":-46.97},\"capacity\":2000,\"limit\":1800,\"radiusMeters\":80}" \
  | json 'd["id"]')
for v in 700 1100 1500 1850; do
  "${C[@]}" -o /dev/null -X POST "$API_URL/telemetry/readings" -H "X-Device-Key: $DEVICE_KEY" \
    -H 'Content-Type: application/json' -d "{\"sensorId\":\"$SENSOR\",\"volume\":$v}"
  sleep 1
done
sleep 2
echo "== POST /sensors/$SENSOR/insights (equipamento $PREFIXO com 4 leituras, a última acima do limite)"
INICIO=$(date +%s)
R=$("${C[@]}" -X POST "$API_URL/sensors/$SENSOR/insights" -H "Authorization: Bearer $OPER" \
  -H 'Content-Type: application/json' -d '{"period":"7d"}')
echo "$R" | python3 -c "import sys,json; d=json.load(sys.stdin); print(json.dumps(d, ensure_ascii=False, indent=2))"
GERADO=$(echo "$R" | json 'str(d["aiGenerated"]).lower()')
echo "== aiGenerated=$GERADO (esperado $ESPERADO) em $(( $(date +%s) - INICIO ))s"
[ "$GERADO" = "$ESPERADO" ] || { echo "FALHOU"; exit 1; }
echo "== OK"
