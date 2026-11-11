-- =====================================================================================
-- Saneia · migração v2 (Oracle) para bancos criados ANTES desta versão
-- - endereço estruturado do equipamento (postal_*)
-- - prefixo único entre equipamentos ativos (active_prefix_key + UNIQUE)
-- Bancos novos não precisam deste script: scripts/script-bd-oracle.sql já cria tudo.
-- O bloco PARA com erro se encontrar prefixos ativos duplicados (nada é alterado).
-- =====================================================================================

-- 1) Diagnóstico: equipamentos ATIVOS que repetem o prefixo (sem diferenciar maiúsculas)
SELECT UPPER(prefix) AS prefixo, COUNT(*) AS ativos,
       LISTAGG(id, ', ') WITHIN GROUP (ORDER BY id) AS ids
  FROM sn_sensors
 WHERE active = 1
 GROUP BY UPPER(prefix)
HAVING COUNT(*) > 1;

-- 2) a 5) Tratamento explícito das duplicatas, colunas, preenchimento e restrição
DECLARE
  v_dup NUMBER;
  v_col NUMBER;
  v_con NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_dup FROM (
    SELECT 1 FROM sn_sensors WHERE active = 1 GROUP BY UPPER(prefix) HAVING COUNT(*) > 1);
  IF v_dup > 0 THEN
    RAISE_APPLICATION_ERROR(-20001, 'Há prefixos ativos duplicados (veja a consulta 1). '
      || 'Renomeie ou retire os excedentes pela API e rode de novo.');
  END IF;

  SELECT COUNT(*) INTO v_col FROM user_tab_columns WHERE table_name = 'SN_SENSORS' AND column_name = 'ACTIVE_PREFIX_KEY';
  IF v_col = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE sn_sensors ADD (active_prefix_key NVARCHAR2(30))';
  END IF;
  SELECT COUNT(*) INTO v_col FROM user_tab_columns WHERE table_name = 'SN_SENSORS' AND column_name = 'POSTAL_CEP';
  IF v_col = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE sn_sensors ADD (postal_cep NVARCHAR2(8), postal_street NVARCHAR2(120), '
      || 'postal_number NVARCHAR2(10), postal_neighborhood NVARCHAR2(80), postal_city NVARCHAR2(80), '
      || 'postal_state NVARCHAR2(2), postal_complement NVARCHAR2(80))';
  END IF;

  EXECUTE IMMEDIATE 'UPDATE sn_sensors SET active_prefix_key = CASE WHEN active = 1 THEN UPPER(prefix) END';

  SELECT COUNT(*) INTO v_con FROM user_constraints WHERE constraint_name = 'UK_SN_SENSORS_ACTIVE_PREFIX';
  IF v_con = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE sn_sensors ADD CONSTRAINT uk_sn_sensors_active_prefix UNIQUE (active_prefix_key)';
  END IF;
  COMMIT;
END;
/

-- 6) Conferência
SELECT id, prefix, active, active_prefix_key, postal_cep, postal_state FROM sn_sensors ORDER BY id;
