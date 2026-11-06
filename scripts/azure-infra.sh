#!/usr/bin/env bash
# =====================================================================================
# Saneia · Provisionamento da infraestrutura no Azure via Azure CLI
#
# Cria: Resource Group, Azure SQL (servidor + banco), RabbitMQ no Azure Container Instances,
#       App Service Plan Linux e Web App Java 21 com todas as variáveis de ambiente da API.
# O Build (CI) e a Release (CD) do Azure DevOps publicam a API neste Web App.
#
# Uso (Azure Cloud Shell em modo Bash ou Git Bash com Azure CLI):
#   az login
#   export RM=rm123456                      # identifica os recursos do grupo (somente letras minúsculas e números)
#   export SQL_ADMIN_PASSWORD='Senha-Forte-123'   # mínimo 8 caracteres com maiúscula, minúscula, número e símbolo
#   bash scripts/azure-infra.sh
#
# Variáveis opcionais: LOCATION, GEMINI_API_KEY, CLIENT_IP (libera seu IP no firewall do SQL para o Query editor),
#                      SQL_TIER (free | basic), APP_SKU (B1 | F1), SEED_PASSWORD (senha das contas de teste).
# Nenhuma senha fica gravada no repositório: tudo vem de variáveis de ambiente ou é gerado aqui.
# =====================================================================================
set -euo pipefail

: "${RM:?Defina RM, por exemplo: export RM=rm123456}"
: "${SQL_ADMIN_PASSWORD:?Defina SQL_ADMIN_PASSWORD com uma senha forte}"

RM_LOWER=$(echo "$RM" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9')
LOCATION="${LOCATION:-brazilsouth}"
RESOURCE_GROUP="${RESOURCE_GROUP:-rg-saneia-${RM_LOWER}}"
SQL_SERVER="${SQL_SERVER:-sql-saneia-${RM_LOWER}}"
SQL_DB="${SQL_DB:-saneiadb}"
SQL_ADMIN="${SQL_ADMIN:-saneiaadmin}"
SQL_TIER="${SQL_TIER:-free}"
RABBIT_NAME="${RABBIT_NAME:-aci-rabbit-saneia-${RM_LOWER}}"
RABBIT_DNS="${RABBIT_DNS:-rabbit-saneia-${RM_LOWER}}"
RABBIT_USER="${RABBIT_USER:-saneia}"
RABBIT_PASSWORD="${RABBIT_PASSWORD:-$(openssl rand -hex 12)}"
APP_PLAN="${APP_PLAN:-plan-saneia-${RM_LOWER}}"
APP_SKU="${APP_SKU:-B1}"
WEBAPP="${WEBAPP:-saneia-api-${RM_LOWER}}"
JWT_SECRET="${JWT_SECRET:-$(openssl rand -hex 32)}"
FILE_SIGNING_SECRET="${FILE_SIGNING_SECRET:-$(openssl rand -hex 32)}"
DEVICE_KEY="${DEVICE_KEY:-$(openssl rand -hex 16)}"
SEED_PASSWORD="${SEED_PASSWORD:-Saneia2026!}"
GEMINI_API_KEY="${GEMINI_API_KEY:-}"

echo ">> Assinatura atual:"
az account show --query "{assinatura:name, id:id}" -o table

echo ">> Registrando provedores usados (só na primeira vez demora)"
for provider in Microsoft.Web Microsoft.Sql Microsoft.ContainerInstance; do
  az provider register --namespace "$provider" --wait -o none
done

echo ">> 1/6 Resource Group ${RESOURCE_GROUP} em ${LOCATION}"
az group create --name "$RESOURCE_GROUP" --location "$LOCATION" \
  --tags projeto=saneia disciplina=devops gs=2026-2 -o none

echo ">> 2/6 Azure SQL Server ${SQL_SERVER}"
az sql server create --name "$SQL_SERVER" --resource-group "$RESOURCE_GROUP" --location "$LOCATION" \
  --admin-user "$SQL_ADMIN" --admin-password "$SQL_ADMIN_PASSWORD" -o none
az sql server firewall-rule create --resource-group "$RESOURCE_GROUP" --server "$SQL_SERVER" \
  --name PermitirServicosAzure --start-ip-address 0.0.0.0 --end-ip-address 0.0.0.0 -o none
if [ -n "${CLIENT_IP:-}" ]; then
  az sql server firewall-rule create --resource-group "$RESOURCE_GROUP" --server "$SQL_SERVER" \
    --name MeuIP --start-ip-address "$CLIENT_IP" --end-ip-address "$CLIENT_IP" -o none
fi

echo ">> 3/6 Banco ${SQL_DB} (tier: ${SQL_TIER})"
if [ "$SQL_TIER" = "free" ]; then
  # Oferta gratuita do Azure SQL (serverless, pausa automática ao atingir o limite mensal)
  az sql db create --resource-group "$RESOURCE_GROUP" --server "$SQL_SERVER" --name "$SQL_DB" \
    --edition GeneralPurpose --compute-model Serverless --family Gen5 --capacity 1 \
    --use-free-limit --free-limit-exhaustion-behavior AutoPause -o none
else
  az sql db create --resource-group "$RESOURCE_GROUP" --server "$SQL_SERVER" --name "$SQL_DB" \
    --service-objective Basic -o none
fi

if command -v sqlcmd >/dev/null 2>&1; then
  echo ">> Executando scripts/script-bd.sql (DDL)"
  sqlcmd -S "${SQL_SERVER}.database.windows.net" -d "$SQL_DB" -U "$SQL_ADMIN" -P "$SQL_ADMIN_PASSWORD" \
    -i "$(dirname "$0")/script-bd.sql" -b || echo "   Aviso: o DDL não rodou aqui; a API cria as tabelas na primeira subida."
else
  echo "   sqlcmd não encontrado: rode scripts/script-bd.sql no Query editor do portal (ou deixe a API criar as tabelas)."
fi

echo ">> 4/6 RabbitMQ no Azure Container Instances (${RABBIT_NAME})"
az container create --resource-group "$RESOURCE_GROUP" --name "$RABBIT_NAME" --location "$LOCATION" \
  --image rabbitmq:3.13-management --os-type Linux --cpu 1 --memory 1.5 \
  --ports 5672 15672 --ip-address Public --dns-name-label "$RABBIT_DNS" \
  --environment-variables RABBITMQ_DEFAULT_USER="$RABBIT_USER" \
  --secure-environment-variables RABBITMQ_DEFAULT_PASS="$RABBIT_PASSWORD" \
  --restart-policy Always -o none
RABBIT_HOST=$(az container show --resource-group "$RESOURCE_GROUP" --name "$RABBIT_NAME" \
  --query ipAddress.fqdn -o tsv)

echo ">> 5/6 App Service Plan ${APP_PLAN} (${APP_SKU}) e Web App ${WEBAPP} (Java 21)"
az appservice plan create --name "$APP_PLAN" --resource-group "$RESOURCE_GROUP" --location "$LOCATION" \
  --is-linux --sku "$APP_SKU" -o none
az webapp create --name "$WEBAPP" --resource-group "$RESOURCE_GROUP" --plan "$APP_PLAN" \
  --runtime "JAVA:21-java21" -o none
if [ "$APP_SKU" != "F1" ]; then
  az webapp config set --name "$WEBAPP" --resource-group "$RESOURCE_GROUP" --always-on true -o none
fi
az webapp config set --name "$WEBAPP" --resource-group "$RESOURCE_GROUP" \
  --generic-configurations '{"healthCheckPath": "/api/actuator/health"}' -o none
az webapp log config --name "$WEBAPP" --resource-group "$RESOURCE_GROUP" \
  --application-logging filesystem --level information -o none

echo ">> 6/6 Variáveis de ambiente da API no Web App"
DB_URL="jdbc:sqlserver://${SQL_SERVER}.database.windows.net:1433;database=${SQL_DB};encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=60;"
az webapp config appsettings set --name "$WEBAPP" --resource-group "$RESOURCE_GROUP" -o none --settings \
  SPRING_PROFILES_ACTIVE=azure \
  SERVER_PORT=80 \
  DB_URL="$DB_URL" \
  DB_USER="$SQL_ADMIN" \
  DB_PASSWORD="$SQL_ADMIN_PASSWORD" \
  RABBITMQ_HOST="$RABBIT_HOST" \
  RABBITMQ_PORT=5672 \
  RABBITMQ_USER="$RABBIT_USER" \
  RABBITMQ_PASSWORD="$RABBIT_PASSWORD" \
  SANEIA_MESSAGING_MODE=rabbit \
  JWT_SECRET="$JWT_SECRET" \
  FILE_SIGNING_SECRET="$FILE_SIGNING_SECRET" \
  DEVICE_KEY="$DEVICE_KEY" \
  GEMINI_API_KEY="$GEMINI_API_KEY" \
  CORS_ORIGINS="*" \
  SEED_ADMIN_PASSWORD="$SEED_PASSWORD" \
  SEED_OPERATOR_PASSWORD="$SEED_PASSWORD" \
  SEED_RESIDENT_PASSWORD="$SEED_PASSWORD" \
  SEED_DEMO_DATA=true

API_URL="https://${WEBAPP}.azurewebsites.net/api"
cat <<RESUMO

=====================================================================================
 Infraestrutura criada. Guarde estes dados (não suba este texto para o repositório):

 Resource Group ........ ${RESOURCE_GROUP}
 Web App ............... ${WEBAPP}
 URL da API ............ ${API_URL}
 Swagger ............... ${API_URL}/swagger-ui.html
 Azure SQL ............. ${SQL_SERVER}.database.windows.net / banco ${SQL_DB} / usuário ${SQL_ADMIN}
 RabbitMQ (painel) ..... http://${RABBIT_HOST}:15672  usuário ${RABBIT_USER} / senha ${RABBIT_PASSWORD}
 DEVICE_KEY (Wokwi) .... ${DEVICE_KEY}

 Próximos passos no Azure DevOps:
   1. Project Settings > Service connections > Azure Resource Manager apontando para ${RESOURCE_GROUP}
   2. Pipelines: azure-pipeline.yml (CI) e azure-pipeline-release.yml (Release) com
      webAppName=${WEBAPP} e resourceGroup=${RESOURCE_GROUP}
   3. No app mobile: EXPO_PUBLIC_API_URL=${API_URL}
=====================================================================================
RESUMO
