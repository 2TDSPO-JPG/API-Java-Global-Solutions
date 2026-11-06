#!/usr/bin/env bash
# Simula o roteiro da demonstração para um equipamento: nível normal → subida → limite crítico → normalização.
# Mesmo formato que a ponte Wokwi/HiveMQ envia para a API.
#   API_URL=https://saneia-api-rm123456.azurewebsites.net/api DEVICE_KEY=xxxx PREFIX=RES-07 bash scripts/simular-leituras.sh
set -euo pipefail
API_URL="${API_URL:-http://localhost:8080/api}"
DEVICE_KEY="${DEVICE_KEY:-local-device-key}"
PREFIX="${PREFIX:-RES-07}"
INTERVALO="${INTERVALO:-5}"
VOLUMES=(1200 1320 1450 1580 1700 1820 1860 1500 1250 1100)

for volume in "${VOLUMES[@]}"; do
  printf '%s  %s -> %s L  ' "$(date +%H:%M:%S)" "$PREFIX" "$volume"
  curl -sS -X POST "${API_URL}/telemetry/readings" \
    -H "Content-Type: application/json" -H "X-Device-Key: ${DEVICE_KEY}" \
    -d "{\"prefix\":\"${PREFIX}\",\"volume\":${volume},\"source\":\"Simulação Wokwi\"}" -w '  [%{http_code}]\n'
  sleep "$INTERVALO"
done
