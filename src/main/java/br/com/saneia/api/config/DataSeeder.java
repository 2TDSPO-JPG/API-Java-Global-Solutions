package br.com.saneia.api.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import br.com.saneia.api.report.Report;
import br.com.saneia.api.report.ReportPriority;
import br.com.saneia.api.report.ReportRepository;
import br.com.saneia.api.report.ReportStatus;
import br.com.saneia.api.sensor.EventKind;
import br.com.saneia.api.sensor.Sensor;
import br.com.saneia.api.sensor.SensorEvent;
import br.com.saneia.api.sensor.SensorEventRepository;
import br.com.saneia.api.sensor.SensorReading;
import br.com.saneia.api.sensor.SensorReadingRepository;
import br.com.saneia.api.sensor.SensorRepository;
import br.com.saneia.api.sensor.SensorStatus;
import br.com.saneia.api.user.AppUser;
import br.com.saneia.api.user.AppUserRepository;
import br.com.saneia.api.user.Role;

/**
 * Provisiona as contas de operadora e administração (o cadastro público só cria moradores) e,
 * opcionalmente, um cenário de demonstração com 4 reservatórios e 2 relatos, igual ao modo local do app.
 * Idempotente: não duplica dados a cada reinício.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEMO_SOURCE = "Carga inicial de demonstração";

    private final SaneiaProperties properties;
    private final AppUserRepository users;
    private final SensorRepository sensors;
    private final SensorReadingRepository readings;
    private final SensorEventRepository events;
    private final ReportRepository reports;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public DataSeeder(SaneiaProperties properties, AppUserRepository users, SensorRepository sensors,
            SensorReadingRepository readings, SensorEventRepository events, ReportRepository reports,
            PasswordEncoder passwordEncoder, TransactionTemplate transaction, Clock clock) {
        this.properties = properties;
        this.users = users;
        this.sensors = sensors;
        this.readings = readings;
        this.events = events;
        this.reports = reports;
        this.passwordEncoder = passwordEncoder;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        SaneiaProperties.Seed seed = properties.seed();
        if (seed == null || !seed.enabled()) {
            return;
        }
        transaction.executeWithoutResult(status -> {
            ensureUser("Administrador", seed.adminEmail(), seed.adminPassword(), Role.ADMIN);
            ensureUser("Equipe operadora", seed.operatorEmail(), seed.operatorPassword(), Role.OPERATOR);
            AppUser resident = ensureUser("Morador", seed.residentEmail(), seed.residentPassword(), Role.RESIDENT);
            if (seed.demoData() && sensors.count() == 0) {
                seedDemo(resident);
            }
        });
    }

    private AppUser ensureUser(String name, String email, String password, Role role) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return users.findByEmailIgnoreCase(normalized).orElseGet(() -> {
            if (!StringUtils.hasText(password)) {
                log.warn("Conta {} ({}) não provisionada: defina a senha na variável de ambiente correspondente.",
                        normalized, role.apiValue());
                return null;
            }
            log.info("Provisionando conta {} com papel {}", normalized, role.apiValue());
            return users.save(new AppUser(name, normalized, passwordEncoder.encode(password), role, clock.instant()));
        });
    }

    private void seedDemo(AppUser resident) {
        Instant now = clock.instant();
        Sensor s07 = sensor("RES-07", "Reservatório 07", "Setor Norte", "Setor Norte · ponto 07",
                -23.5492, -46.6370, 2000, 1800, 220, now);
        Sensor s09 = sensor("RES-09", "Reservatório 09", "Setor Norte", "Setor Norte · ponto 09",
                -23.5504, -46.6318, 2000, 1800, 220, now);
        Sensor s12 = sensor("RES-12", "Reservatório 12", "Setor Central", "Setor Central · ponto 12",
                -23.5521, -46.6336, 1500, 1250, 210, now);
        Sensor s19 = sensor("RES-19", "Reservatório 19", "Setor Sul", "Setor Sul · ponto 19",
                -23.5540, -46.6364, 2200, 1950, 200, now);

        history(s07, now, new double[] {1430, 1465, 1510, 1550, 1600}, Duration.ofMinutes(3));
        history(s09, now, new double[] {1540, 1660, 1740, 1810, 1880}, Duration.ofMinutes(3));
        history(s12, now, new double[] {440, 490, 530, 610, 650}, Duration.ofMinutes(2));
        history(s19, now.minus(Duration.ofHours(11)), new double[] {600, 625, 660, 690, 720}, Duration.ZERO);

        state(s07, SensorStatus.WARNING, "Volume acima de 85% do limite operacional.", now);
        events.save(new SensorEvent(s07, EventKind.LEVEL, SensorStatus.WARNING, now.minus(Duration.ofMinutes(30)),
                "Volume acima de 85% do limite operacional.", 1600d));
        state(s09, SensorStatus.ALARM, "Limite operacional atingido: 1880 L de 1800 L.", now);
        events.save(new SensorEvent(s09, EventKind.LEVEL, SensorStatus.ALARM, now.minus(Duration.ofMinutes(18)),
                "O volume ultrapassou 1.800 L.", 1880d));
        state(s12, SensorStatus.NORMAL, "Nível dentro da faixa normal.", now);
        state(s19, SensorStatus.OFFLINE, "Sem comunicação desde a última leitura; verificar o dispositivo.", now);
        events.save(new SensorEvent(s19, EventKind.COMMUNICATION, SensorStatus.OFFLINE,
                now.minus(Duration.ofHours(11)), "O equipamento deixou de enviar leituras.", null));

        // Alarmes passados (contam em alarmsSixMonths e aparecem nos insights).
        pastAlarm(s07, now.minus(Duration.ofDays(52)), 2);
        pastAlarm(s07, now.minus(Duration.ofDays(120)), 1);
        pastAlarm(s09, now.minus(Duration.ofDays(25)), 3);
        pastAlarm(s12, now.minus(Duration.ofDays(14)), 1);

        if (resident != null) {
            Report r1 = new Report("SAN-DEMO-0001", resident, now.minus(Duration.ofDays(30)));
            r1.applyContent("Vazamento", "Há água com cheiro forte saindo na calçada, perto do ponto de ônibus.",
                    "Setor Leste · próximo ao ponto de ônibus", "Setor Leste", -23.5514, -46.6309, r1.getCreatedAt());
            r1.prioritize(ReportPriority.MEDIUM, now); // aberto há 30 dias: aparece como fora do prazo
            reports.save(r1);
            Report r2 = new Report("SAN-DEMO-0002", resident, now.minus(Duration.ofDays(21)));
            r2.applyContent("Mau cheiro", "O cheiro aparece à tarde perto da entrada da viela.",
                    "Setor Norte · entrada da viela", "Setor Norte", -23.5495, -46.6373, r2.getCreatedAt());
            r2.linkSensor(s07);
            r2.prioritize(ReportPriority.HIGH, now);
            r2.changeStatus(ReportStatus.IN_PROGRESS, null, "A equipe aguarda a próxima vistoria no local.",
                    r2.getCreatedAt().plus(Duration.ofDays(1)));
            reports.save(r2);
        }
        log.info("Cenário de demonstração criado: 4 equipamentos, leituras, eventos e 2 relatos.");
    }

    private Sensor sensor(String prefix, String name, String neighborhood, String address, double latitude,
            double longitude, double capacity, double limit, double radius, Instant now) {
        Sensor sensor = new Sensor(now.minus(Duration.ofDays(180)));
        sensor.updateCatalog(prefix, name, "Reservatório com sensor de nível", address, neighborhood, latitude,
                longitude, capacity, limit, radius, properties.telemetry().defaultExpectedIntervalMinutes(), now);
        return sensors.save(sensor);
    }

    /** Histórico diário de 120 dias + leituras recentes no intervalo informado, terminando em "end". */
    private void history(Sensor sensor, Instant end, double[] recent, Duration step) {
        double[] daily = {0.44, 0.52, 0.61, 0.47, 0.70, 0.66, 0.58};
        for (int day = 120; day >= 1; day -= 3) {
            double volume = Math.round(sensor.getCapacity() * daily[day % daily.length]);
            Instant at = end.minus(Duration.ofDays(day));
            readings.save(new SensorReading(sensor, at, at, volume, levelCm(sensor, volume),
                    volume >= sensor.getLimit() ? SensorStatus.ALARM : SensorStatus.NORMAL, DEMO_SOURCE, null));
        }
        Duration gap = step.isZero() ? Duration.ofMinutes(30) : step;
        for (int i = 0; i < recent.length; i++) {
            Instant at = end.minus(gap.multipliedBy(recent.length - 1L - i));
            double volume = recent[i];
            SensorStatus status = volume >= sensor.getLimit() ? SensorStatus.ALARM
                    : volume >= sensor.getLimit() * properties.telemetry().warningRatio() ? SensorStatus.WARNING
                    : SensorStatus.NORMAL;
            readings.save(new SensorReading(sensor, at, at, volume, levelCm(sensor, volume), status, DEMO_SOURCE, null));
            if (i == recent.length - 1) {
                sensor.applyReading(volume, levelCm(sensor, volume), at);
            }
        }
    }

    private void pastAlarm(Sensor sensor, Instant start, int hours) {
        SensorEvent event = new SensorEvent(sensor, EventKind.LEVEL, SensorStatus.ALARM, start,
                "Limite operacional atingido.", sensor.getLimit() + 40);
        event.close(start.plus(Duration.ofHours(hours)));
        events.save(event);
        readings.save(new SensorReading(sensor, start, start, sensor.getLimit() + 40,
                levelCm(sensor, sensor.getLimit() + 40), SensorStatus.ALARM, DEMO_SOURCE, null));
    }

    private static void state(Sensor sensor, SensorStatus status, String reason, Instant now) {
        sensor.applyState(status, reason, now);
    }

    private static Double levelCm(Sensor sensor, double volume) {
        return Math.round(volume / sensor.getCapacity() * 2000) / 10.0;
    }
}
