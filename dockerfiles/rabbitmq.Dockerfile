# RabbitMQ com painel de gestão (porta 15672). O script scripts/azure-infra.sh sobe esta mesma versão no ACI.
FROM rabbitmq:3.13-management
EXPOSE 5672 15672
