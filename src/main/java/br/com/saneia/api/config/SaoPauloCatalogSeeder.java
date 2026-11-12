package br.com.saneia.api.config;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.saneia.api.sensor.PostalAddress;
import br.com.saneia.api.sensor.Sensor;
import br.com.saneia.api.sensor.SensorRepository;

@Component
@Profile("local")
@Order(1)
@ConditionalOnProperty(name = "saneia.seed.sp-catalog", havingValue = "true", matchIfMissing = true)
public class SaoPauloCatalogSeeder implements ApplicationRunner {

    public static final String PREFIX = "SP-D";
    private static final Logger log = LoggerFactory.getLogger(SaoPauloCatalogSeeder.class);
    private static final int[] CAPACITIES = {1500, 2000, 2500, 3000, 4000, 5000};
    private final SensorRepository sensors;
    private final ObjectMapper json;
    private final Clock clock;

    public SaoPauloCatalogSeeder(SensorRepository sensors, ObjectMapper json, Clock clock) {
        this.sensors = sensors;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        Site[] sites;
        try (var stream = new ClassPathResource("demo/sao-paulo-sites.json").getInputStream()) {
            sites = json.readValue(stream, Site[].class);
        }
        Set<String> existing = sensors.findByPrefixStartingWith(PREFIX).stream()
                .map(Sensor::getPrefix).collect(Collectors.toSet());
        Instant now = clock.instant();
        int created = 0;
        for (int i = 0; i < sites.length; i++) {
            String prefix = PREFIX + String.format(Locale.ROOT, "%03d", i + 1);
            if (existing.contains(prefix)) {
                continue;
            }
            Site site = sites[i];
            int capacity = CAPACITIES[i % CAPACITIES.length];
            save(prefix, "Ponto simulado • " + site.district(), site, capacity, capacity * 0.9, 30, now);
            created++;
        }
        if (!sensors.existsByPrefixIgnoreCase("RES-07")) {
            save("RES-07", "Reservatório de teste Wokwi",
                    new Site("Sé", "Sé", "Praça da Sé", "S/N", "01001000", -23.5503, -46.6337),
                    2000, 1800, 1, now);
            created++;
        }
        log.info("Catálogo de São Paulo: {} ponto(s) de demonstração cadastrado(s).", created);
    }

    private void save(String prefix, String name, Site site, int capacity, double limit, int interval, Instant now) {
        String address = "%s, %s · %s · São Paulo/SP".formatted(site.street(), site.number(), site.neighborhood());
        Sensor sensor = new Sensor(now);
        sensor.updateCatalog(prefix, name, "Reservatório com sensor de nível", address, site.neighborhood(),
                site.latitude(), site.longitude(), capacity, limit, 200, interval, now);
        sensor.updatePostalAddress(new PostalAddress(site.cep(), site.street(), site.number(), site.neighborhood(),
                "São Paulo", "SP", null));
        sensors.save(sensor);
    }

    private record Site(String district, String neighborhood, String street, String number, String cep,
            double latitude, double longitude) {
    }
}
