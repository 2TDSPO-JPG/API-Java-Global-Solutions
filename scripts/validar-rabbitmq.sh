#!/usr/bin/env bash
# Prova de ponta a ponta da mensageria com RabbitMQ REAL (requisito de arquitetura distribuída).
# Pré-requisitos: broker no ar e API rodando em modo rabbit:
#   docker compose -f dockerfiles/docker-compose.yml up -d rabbitmq
#   SANEIA_MESSAGING_MODE=rabbit RABBIT_HEALTH=true ./mvnw spring-boot:run
#   bash scripts/validar-rabbitmq.sh
# O que o roteiro comprova:
#   1) a API publica a leitura no exchange saneia.events e responde 202 com a fila de destino;
#   2) a mensagem passa pela fila saneia.telemetry.readings (contadores do RabbitMQ);
#   3) o listener consome e GRAVA a leitura (efeito persistido, visto pela API e pelo banco);
#   4) a mudança de status do relato gera report.status, consumido em saneia.notifications.
set -euo pipefail
API_URL="${API_URL:-http://localhost:8080/api}"
RABBIT_API="${RABBIT_API:-http://localhost:15672/api}"
RABBIT_AUTH="${RABBIT_AUTH:-guest:guest}"
DEVICE_KEY="${DEVICE_KEY:-local-device-key}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@saneia.test}"
OPERATOR_EMAIL="${OPERATOR_EMAIL:-operadora@saneia.test}"
RESIDENT_EMAIL="${RESIDENT_EMAIL:-morador@saneia.test}"
SENHA="${SENHA:-Saneia2026!}"
C=(curl --noproxy '*' -sS)

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)"; }
login() { "${C[@]}" -X POST "$API_URL/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$1\",\"password\":\"$SENHA\"}" | json 'd["token"]'; }
fila() { "${C[@]}" -u "$RABBIT_AUTH" "$RABBIT_API/queues/%2F/$1" \
  | json "str(d.get('message_stats',{}).get('publish',0))+' publicadas / '+str(d.get('message_stats',{}).get('ack',0))+' consumidas / '+str(d.get('messages',0))+' na fila / '+str(d.get('consumers',0))+' consumidor(es)'"; }

echo "== Broker: $("${C[@]}" -u "$RABBIT_AUTH" "$RABBIT_API/overview" | json 'd["product_name"]+" "+d["rabbitmq_version"]')"
consumidores() { "${C[@]}" -u "$RABBIT_AUTH" "$RABBIT_API/queues/%2F/$1" | json 'd.get("consumers",0)'; }
echo "== API em modo rabbit: aguardando os consumidores conectarem nas filas..."
for i in $(seq 1 20); do
  [ "$(consumidores saneia.telemetry.readings)" -ge 1 ] && [ "$(consumidores saneia.notifications)" -ge 1 ] && break; sleep 1
done
[ "$(consumidores saneia.telemetry.readings)" -ge 1 ] || { echo "FALHOU: nenhum consumidor. A API está com SANEIA_MESSAGING_MODE=rabbit?"; exit 1; }
echo "   telemetria:    $(fila saneia.telemetry.readings)"
echo "   notificações:  $(fila saneia.notifications)"

ADMIN=$(login "$ADMIN_EMAIL"); OPER=$(login "$OPERATOR_EMAIL"); MORADOR=$(login "$RESIDENT_EMAIL")
PREFIXO="RMQ-$(date +%H%M%S)"
SENSOR=$("${C[@]}" -X POST "$API_URL/admin/sensors" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d "{\"prefix\":\"$PREFIXO\",\"name\":\"Validação RabbitMQ\",\"equipmentType\":\"Reservatório com sensor de nível\",\"address\":\"Rua da Fila, 1\",\"neighborhood\":\"Centro\",\"coordinate\":{\"latitude\":-23.95,\"longitude\":-46.95},\"capacity\":2000,\"limit\":1800,\"radiusMeters\":80}" \
  | json 'd["id"]')
echo "== 1) Equipamento $PREFIXO criado (id $SENSOR). Publicando leitura de 1234 L..."
"${C[@]}" -X POST "$API_URL/telemetry/readings" -H "X-Device-Key: $DEVICE_KEY" -H 'Content-Type: application/json' \
  -d "{\"sensorId\":\"$SENSOR\",\"volume\":1234,\"source\":\"validar-rabbitmq.sh\"}" -w '   HTTP %{http_code}\n'

echo "== 2/3) Aguardando o consumidor gravar a leitura..."
for i in $(seq 1 30); do
  VOL=$("${C[@]}" "$API_URL/sensors/$SENSOR" -H "Authorization: Bearer $OPER" | json 'd["volume"]')
  [ "$VOL" = "1234.0" ] && break; sleep 1
done
[ "$VOL" = "1234.0" ] || { echo "FALHOU: leitura não foi consumida (volume=$VOL)"; exit 1; }
echo "   OK: volume gravado = $VOL L após ${i}s (consumido pelo listener telemetry-consumer)"
echo "   Conferência no banco: SELECT * FROM sn_sensor_readings WHERE sensor_id = $SENSOR;"

echo "== 4) Relato do morador + status pela operadora (report.status → saneia.notifications)"
ANTES=$("${C[@]}" "$API_URL/notifications" -H "Authorization: Bearer $MORADOR" | json 'len(d)')
LAT="-23.9$(date +%S)1"
RELATO=$("${C[@]}" -X POST "$API_URL/reports" -H "Authorization: Bearer $MORADOR" -H 'Content-Type: application/json' \
  -d "{\"category\":\"Outro problema\",\"description\":\"Validação da fila de notificações.\",\"address\":\"Rua da Fila, 1\",\"neighborhood\":\"Centro\",\"coordinate\":{\"latitude\":$LAT,\"longitude\":-46.951}}" \
  | json 'd["id"]')
"${C[@]}" -X PATCH "$API_URL/reports/$RELATO/status" -H "Authorization: Bearer $OPER" -H 'Content-Type: application/json' \
  -d '{"status":"in_progress","progressNote":"Equipe a caminho (validação RabbitMQ)."}' -o /dev/null -w '   status do relato: HTTP %{http_code}\n'
for i in $(seq 1 30); do
  DEPOIS=$("${C[@]}" "$API_URL/notifications" -H "Authorization: Bearer $MORADOR" | json 'len(d)')
  [ "$DEPOIS" -gt "$ANTES" ] && break; sleep 1
done
[ "$DEPOIS" -gt "$ANTES" ] || { echo "FALHOU: notificação não foi gerada"; exit 1; }
echo "   OK: notificações do morador $ANTES → $DEPOIS (consumidas pelo notification-consumer)"
sleep 6  # o painel do RabbitMQ atualiza os contadores a cada 5 s
echo "== Contadores finais no broker"
echo "   telemetria:    $(fila saneia.telemetry.readings)"
echo "   notificações:  $(fila saneia.notifications)"
echo "== Mensageria assíncrona validada com o broker real."
