/* =====================================================================================
   Saneia · migração v2 (SQL Server / Azure SQL) para bancos criados ANTES desta versão
   - endereço estruturado do equipamento (postal_*)
   - prefixo único entre equipamentos ativos (active_prefix_key + índice único filtrado)
   Bancos novos não precisam deste script: scripts/script-bd.sql já cria tudo.
   O script é idempotente e PARA com erro se encontrar prefixos ativos duplicados.
   ===================================================================================== */

/* 1) Diagnóstico: equipamentos ATIVOS que repetem o prefixo (sem diferenciar maiúsculas) */
SELECT UPPER(prefix) AS prefixo, COUNT(*) AS ativos, STRING_AGG(CAST(id AS NVARCHAR(20)), ', ') AS ids
  FROM dbo.sn_sensors
 WHERE active = 1
 GROUP BY UPPER(prefix)
HAVING COUNT(*) > 1;
GO

/* 2) Tratamento explícito: com duplicatas, nada é alterado. Escolha qual equipamento mantém o prefixo e
      renomeie (PATCH /admin/sensors/{id}) ou retire (DELETE /admin/sensors/{id}) os demais. Depois rode de novo. */
IF EXISTS (SELECT 1 FROM dbo.sn_sensors WHERE active = 1 GROUP BY UPPER(prefix) HAVING COUNT(*) > 1)
    THROW 50001, 'Há prefixos ativos duplicados (veja a consulta 1). Resolva antes de criar a restrição.', 1;
GO

/* 3) Novas colunas */
IF COL_LENGTH('dbo.sn_sensors', 'active_prefix_key') IS NULL
    ALTER TABLE dbo.sn_sensors ADD active_prefix_key NVARCHAR(30) NULL;
IF COL_LENGTH('dbo.sn_sensors', 'postal_cep') IS NULL
    ALTER TABLE dbo.sn_sensors ADD postal_cep NVARCHAR(8) NULL, postal_street NVARCHAR(120) NULL,
        postal_number NVARCHAR(10) NULL, postal_neighborhood NVARCHAR(80) NULL, postal_city NVARCHAR(80) NULL,
        postal_state NVARCHAR(2) NULL, postal_complement NVARCHAR(80) NULL;
GO

/* 4) Preenche a chave dos ativos e limpa a dos retirados */
UPDATE dbo.sn_sensors SET active_prefix_key = CASE WHEN active = 1 THEN UPPER(prefix) END;
GO

/* 5) Restrição: um único ativo por prefixo (índice filtrado: retirados com chave nula não conflitam) */
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uk_sn_sensors_active_prefix'
                 AND object_id = OBJECT_ID('dbo.sn_sensors'))
    CREATE UNIQUE INDEX uk_sn_sensors_active_prefix ON dbo.sn_sensors (active_prefix_key)
        WHERE active_prefix_key IS NOT NULL;
GO

/* 6) Conferência */
SELECT id, prefix, active, active_prefix_key, postal_cep, postal_state FROM dbo.sn_sensors ORDER BY id;
