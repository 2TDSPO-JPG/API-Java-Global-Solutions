package br.com.saneia.api.sensor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bancos criados antes da restrição de prefixo (ddl-auto=update) ganham a coluna active_prefix_key vazia.
 * Na subida, preenche a chave dos equipamentos ativos. Duplicatas não são resolvidas em silêncio: o mais antigo
 * fica com o prefixo e os demais são listados no log, para o admin renomear ou retirar pelo painel ou pela API.
 * Os scripts scripts/migracao-v2-*.sql fazem a mesma verificação direto no banco.
 */
@Component
@Order(0)
public class SensorPrefixKeyBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SensorPrefixKeyBackfill.class);

    private final SensorRepository sensors;

    public SensorPrefixKeyBackfill(SensorRepository sensors) {
        this.sensors = sensors;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Sensor> pending = sensors.findByActiveTrueAndActivePrefixKeyIsNullOrderByIdAsc();
        if (pending.isEmpty()) {
            return;
        }
        List<String> duplicates = new ArrayList<>();
        int filled = 0;
        for (Sensor sensor : pending) {
            String key = sensor.getPrefix().toUpperCase(Locale.ROOT);
            if (sensors.existsByActivePrefixKey(key)) {
                duplicates.add(sensor.getPrefix() + " (id " + sensor.getId() + ")");
                continue;
            }
            sensors.assignActivePrefixKey(sensor.getId(), key);
            filled++;
        }
        log.info("Chave de prefixo ativo preenchida em {} equipamento(s).", filled);
        if (!duplicates.isEmpty()) {
            log.warn("Prefixos ativos duplicados encontrados e NÃO protegidos pela restrição: {}. "
                    + "Renomeie ou retire esses equipamentos (PATCH/DELETE /admin/sensors/{id}).", duplicates);
        }
    }
}
