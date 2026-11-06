#!/usr/bin/env bash
# Remove todos os recursos do Saneia (use depois da avaliação para não consumir créditos).
#   export RM=rm123456 && bash scripts/azure-cleanup.sh
set -euo pipefail
: "${RM:?Defina RM, por exemplo: export RM=rm123456}"
RM_LOWER=$(echo "$RM" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9')
RESOURCE_GROUP="${RESOURCE_GROUP:-rg-saneia-${RM_LOWER}}"
echo "Excluindo o Resource Group ${RESOURCE_GROUP} e tudo que está nele..."
az group delete --name "$RESOURCE_GROUP" --yes --no-wait
echo "Exclusão iniciada. Acompanhe no portal Azure."
