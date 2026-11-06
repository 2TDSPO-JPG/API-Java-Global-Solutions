package br.com.saneia.api.panel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import br.com.saneia.api.alert.AlertEmailService;
import br.com.saneia.api.alert.AlertRecipient;
import br.com.saneia.api.alert.AlertRecipientRepository;
import br.com.saneia.api.alert.EmailLog;
import br.com.saneia.api.alert.EmailLogRepository;
import br.com.saneia.api.common.ApiException;
import br.com.saneia.api.file.FileService;
import br.com.saneia.api.panel.PanelViews.AreaRisk;
import br.com.saneia.api.panel.PanelViews.Dashboard;
import br.com.saneia.api.panel.PanelViews.EventRow;
import br.com.saneia.api.panel.PanelViews.Kpis;
import br.com.saneia.api.panel.PanelViews.ReadingRow;
import br.com.saneia.api.panel.PanelViews.ReportDetail;
import br.com.saneia.api.panel.PanelViews.ReportRow;
import br.com.saneia.api.panel.PanelViews.RiskLevel;
import br.com.saneia.api.panel.PanelViews.SensorDetail;
import br.com.saneia.api.panel.PanelViews.SensorRow;
import br.com.saneia.api.panel.PanelViews.UpdateRow;
import br.com.saneia.api.panel.PanelViews.UserRow;
import br.com.saneia.api.report.Report;
import br.com.saneia.api.report.ReportRepository;
import br.com.saneia.api.report.ReportStatus;
import br.com.saneia.api.sensor.EventKind;
import br.com.saneia.api.sensor.Forecast;
import br.com.saneia.api.sensor.Sensor;
import br.com.saneia.api.sensor.SensorEvent;
import br.com.saneia.api.sensor.SensorEventRepository;
import br.com.saneia.api.sensor.SensorReading;
import br.com.saneia.api.sensor.SensorReadingRepository;
import br.com.saneia.api.sensor.SensorRepository;
import br.com.saneia.api.sensor.SensorService;
import br.com.saneia.api.sensor.SensorStatus;
import br.com.saneia.api.user.AppUser;
import br.com.saneia.api.user.AppUserRepository;
import br.com.saneia.api.user.Role;

/** Consultas e ações do painel administrativo: visão total dos dados, risco por área e gestão. */
@Service
public class PanelService {

    private static final int RECURRENT_HIGH = 1;
    private static final int OPEN_REPORTS_HIGH = 3;

    private final ReportRepository reports;
    private final SensorRepository sensors;
    private final SensorReadingRepository readings;
    private final SensorEventRepository events;
    private final SensorService sensorService;
    private final AppUserRepository users;
    private final AlertRecipientRepository recipients;
    private final EmailLogRepository emailLogs;
    private final AlertEmailService alertEmails;
    private final FileService files;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public PanelService(ReportRepository reports, SensorRepository sensors, SensorReadingRepository readings,
            SensorEventRepository events, SensorService sensorService, AppUserRepository users,
            AlertRecipientRepository recipients, EmailLogRepository emailLogs, AlertEmailService alertEmails,
            FileService files, PasswordEncoder passwordEncoder, Clock clock) {
        this.reports = reports;
        this.sensors = sensors;
        this.readings = readings;
        this.events = events;
        this.sensorService = sensorService;
        this.users = users;
        this.recipients = recipients;
        this.emailLogs = emailLogs;
        this.alertEmails = alertEmails;
        this.files = files;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- visão geral

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        List<SensorRow> sensorRows = sensors.findByActiveTrue().stream().map(this::sensorRow).toList();
        List<SensorRow> atRisk = sensorRows.stream()
                .filter(s -> !"normal".equals(s.status()))
                .sorted(Comparator.comparingInt(PanelService::severityOrder).thenComparing(SensorRow::prefix))
                .toList();
        return new Dashboard(kpis(sensorRows), areas(sensorRows),
                reports.findTop8ByDeletedAtIsNullOrderByCreatedAtDescIdDesc().stream().map(this::reportRow).toList(),
                events.findTop10ByOrderByStartedAtDescIdDesc().stream().map(PanelService::eventRow).toList(),
                atRisk);
    }

    private Kpis kpis(List<SensorRow> sensorRows) {
        long received = reports.countByStatusAndDeletedAtIsNull(ReportStatus.RECEIVED);
        long inProgress = reports.countByStatusAndDeletedAtIsNull(ReportStatus.IN_PROGRESS);
        long closed = reports.countByStatusAndDeletedAtIsNull(ReportStatus.CLOSED);
        List<Report> closedReports = reports.findByStatusAndDeletedAtIsNullAndClosedAtIsNotNull(ReportStatus.CLOSED);
        String average = closedReports.isEmpty() ? "—" : formatDuration(Duration.ofSeconds(Math.round(closedReports.stream()
                .mapToLong(r -> Duration.between(r.getCreatedAt(), r.getClosedAt()).getSeconds())
                .average().orElse(0))));
        Instant since = clock.instant().minus(Duration.ofHours(24));
        return new Kpis(received + inProgress, received, inProgress, closed,
                reports.countByDeletedAtIsNullAndStatusNotAndPreviousReportIsNotNull(ReportStatus.CLOSED),
                reports.countByDeletedAtIsNotNull(), average, sensorRows.size(),
                count(sensorRows, "alarm"), count(sensorRows, "warning"), count(sensorRows, "offline"),
                count(sensorRows, "normal"), users.count(),
                emailLogs.countByStatusAndCreatedAtGreaterThanEqual(EmailLog.Status.SENT, since),
                emailLogs.countByStatusAndCreatedAtGreaterThanEqual(EmailLog.Status.FAILED, since),
                alertEmails.smtpConfigured());
    }

    /**
     * Risco por área (bairro): junta o estado dos sensores com os chamados abertos.
     * Risco máximo = algum equipamento em alarme. Alto = alerta de nível, recorrência ou 3+ chamados abertos.
     * Moderado = equipamento sem comunicação ou chamado aberto. Baixo = sem pendências.
     */
    @Transactional(readOnly = true)
    public List<AreaRisk> areas() {
        return areas(sensors.findByActiveTrue().stream().map(this::sensorRow).toList());
    }

    private List<AreaRisk> areas(List<SensorRow> sensorRows) {
        Map<String, List<SensorRow>> sensorsByArea = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sensorRows.forEach(s -> sensorsByArea.computeIfAbsent(s.neighborhood(), k -> new ArrayList<>()).add(s));
        Map<String, List<Report>> reportsByArea = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        reports.findByDeletedAtIsNullAndStatusNot(ReportStatus.CLOSED)
                .forEach(r -> reportsByArea.computeIfAbsent(r.getNeighborhood(), k -> new ArrayList<>()).add(r));

        Map<String, String> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sensorsByArea.keySet().forEach(n -> names.putIfAbsent(n, n));
        reportsByArea.keySet().forEach(n -> names.putIfAbsent(n, n));

        List<AreaRisk> result = new ArrayList<>();
        for (String area : names.values()) {
            List<SensorRow> areaSensors = sensorsByArea.getOrDefault(area, List.of());
            List<Report> open = reportsByArea.getOrDefault(area, List.of());
            int alarms = (int) areaSensors.stream().filter(s -> "alarm".equals(s.status())).count();
            int warnings = (int) areaSensors.stream().filter(s -> "warning".equals(s.status())).count();
            int offline = (int) areaSensors.stream().filter(s -> "offline".equals(s.status())).count();
            long recurrent = open.stream().filter(r -> r.getPreviousReport() != null).count();
            RiskLevel level;
            String reason;
            if (alarms > 0) {
                level = RiskLevel.CRITICO;
                reason = alarms + " equipamento(s) com limite operacional atingido";
            } else if (warnings > 0 || recurrent >= RECURRENT_HIGH || open.size() >= OPEN_REPORTS_HIGH) {
                level = RiskLevel.ALTO;
                reason = warnings > 0 ? "Nível em alerta ou previsão de atingir o limite"
                        : recurrent > 0 ? "Chamados recorrentes no mesmo local" : "Muitos chamados abertos";
            } else if (offline > 0 || !open.isEmpty()) {
                level = RiskLevel.MODERADO;
                reason = offline > 0 ? "Equipamento sem comunicação" : "Chamado aguardando atendimento";
            } else {
                level = RiskLevel.BAIXO;
                reason = "Sem pendências";
            }
            List<String> alarmPrefixes = areaSensors.stream().filter(s -> "alarm".equals(s.status()))
                    .map(SensorRow::prefix).toList();
            result.add(new AreaRisk(area, level, reason, areaSensors.size(), alarms, warnings, offline, open.size(),
                    recurrent, alarmPrefixes));
        }
        result.sort(Comparator.comparing(AreaRisk::level).thenComparing(AreaRisk::name));
        return result;
    }

    // ---------------------------------------------------------------- chamados

    @Transactional(readOnly = true)
    public Page<ReportRow> reports(String status, String query, boolean includeDeleted, int page) {
        Specification<Report> spec = (root, cq, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (!includeDeleted) {
                filters.add(cb.isNull(root.get("deletedAt")));
            }
            if (StringUtils.hasText(status)) {
                filters.add(cb.equal(root.get("status"), ReportStatus.fromApi(status)));
            }
            if (StringUtils.hasText(query)) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                filters.add(cb.or(
                        cb.like(cb.lower(root.<String>get("protocol")), like),
                        cb.like(cb.lower(root.<String>get("neighborhood")), like),
                        cb.like(cb.lower(root.<String>get("category")), like),
                        cb.like(cb.lower(root.<String>get("address")), like)));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        };
        PageRequest request = PageRequest.of(Math.max(page, 0), 15,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return reports.findAll(spec, request).map(this::reportRow);
    }

    @Transactional(readOnly = true)
    public ReportDetail report(Long id) {
        Report report = reports.findById(id).orElseThrow(() -> ApiException.notFound("Chamado não encontrado."));
        List<UpdateRow> updates = report.getUpdates().stream()
                .map(u -> new UpdateRow(u.getCreatedAt(), u.getNote(),
                        u.getPhoto() == null ? null : files.signedUrl(u.getPhoto().getId())))
                .toList();
        Report previous = report.getPreviousReport();
        return new ReportDetail(reportRow(report), report.getDescription(), report.getLatitude(), report.getLongitude(),
                report.getResolution(), report.getProgressNote(), report.getAssessmentNote(),
                report.getAssessmentRequestedAt(),
                report.getPhoto() == null ? null : files.signedUrl(report.getPhoto().getId()),
                previous == null ? null : previous.getProtocol(), previous == null ? null : previous.getId(), updates);
    }

    // ---------------------------------------------------------------- equipamentos

    @Transactional(readOnly = true)
    public List<SensorRow> sensors() {
        return sensors.findByActiveTrue().stream().map(this::sensorRow)
                .sorted(Comparator.comparingInt(PanelService::severityOrder).thenComparing(SensorRow::prefix))
                .toList();
    }

    @Transactional(readOnly = true)
    public SensorDetail sensor(Long id) {
        Sensor sensor = sensors.findById(id).orElseThrow(() -> ApiException.notFound("Equipamento não encontrado."));
        List<SensorReading> recent = new ArrayList<>(
                readings.findBySensorIdOrderByMeasuredAtDescIdDesc(id, PageRequest.of(0, 40)));
        Collections.reverse(recent);
        List<ReadingRow> readingRows = recent.stream()
                .map(r -> new ReadingRow(r.getMeasuredAt(), r.getVolume(),
                        (int) Math.min(100, Math.round(r.getVolume() / sensor.getCapacity() * 100)),
                        r.getStatus().apiValue(), r.getSource()))
                .toList();
        List<EventRow> eventRows = events.findBySensorIdOrderByStartedAtDescIdDesc(id, PageRequest.of(0, 20))
                .stream().map(PanelService::eventRow).toList();
        List<ReportRow> linked = reports.findBySensorIdAndDeletedAtIsNullOrderByCreatedAtDesc(id).stream()
                .map(this::reportRow).toList();
        return new SensorDetail(sensorRow(sensor), sensor.getLatitude(), sensor.getLongitude(), readingRows, eventRows,
                linked, (int) Math.ceil(sensor.getCapacity()));
    }

    // ---------------------------------------------------------------- usuários

    @Transactional(readOnly = true)
    public Page<UserRow> users(int page) {
        return users.findAllByOrderByIdAsc(PageRequest.of(Math.max(page, 0), 20))
                .map(u -> new UserRow(u.getId(), u.getName(), u.getEmail(), u.getRole().apiValue(), u.getCreatedAt()));
    }

    /** O admin provisiona contas de operadora e de administração (o cadastro público só cria moradores). */
    @Transactional
    public void createUser(PanelForms.NewUser form) {
        String email = form.getEmail().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Já existe uma conta com este e-mail.");
        }
        Role role = switch (form.getRole()) {
            case "operator" -> Role.OPERATOR;
            case "admin" -> Role.ADMIN;
            case "resident" -> Role.RESIDENT;
            default -> throw ApiException.badRequest("Papel inválido.");
        };
        users.save(new AppUser(form.getName().trim(), email, passwordEncoder.encode(form.getPassword()), role,
                clock.instant()));
    }

    // ---------------------------------------------------------------- alertas por e-mail

    @Transactional(readOnly = true)
    public List<AlertRecipient> recipients() {
        return recipients.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Page<EmailLog> emailLogs(int page) {
        return emailLogs.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(Math.max(page, 0), 20));
    }

    @Transactional
    public void addRecipient(PanelForms.NewRecipient form) {
        String email = form.getEmail().trim().toLowerCase(Locale.ROOT);
        if (recipients.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Este e-mail já recebe os alertas.");
        }
        recipients.save(new AlertRecipient(form.getName().trim(), email, clock.instant()));
    }

    @Transactional
    public void toggleRecipient(Long id) {
        recipients.findById(id).orElseThrow(() -> ApiException.notFound("Destinatário não encontrado.")).toggle();
    }

    @Transactional
    public void removeRecipient(Long id) {
        recipients.deleteById(id);
    }

    public EmailLog sendTest(String email) {
        return alertEmails.sendTest(email);
    }

    public boolean smtpConfigured() {
        return alertEmails.smtpConfigured();
    }

    // ---------------------------------------------------------------- mapeamentos

    private ReportRow reportRow(Report r) {
        Sensor sensor = r.getSensor();
        return new ReportRow(r.getId(), r.getProtocol(), r.getCategory(), r.getNeighborhood(), r.getAddress(),
                r.getStatus().apiValue(), r.getCreatedAt(), r.getClosedAt(), r.getOwner().getName(),
                r.getOwner().getEmail(), sensor == null ? null : sensor.getPrefix(),
                r.getPreviousReport() != null && r.isOpen(), r.isDeleted(), r.getPhoto() != null);
    }

    private SensorRow sensorRow(Sensor s) {
        SensorStatus status = sensorService.effectiveStatus(s);
        List<Forecast.Point> recent = new ArrayList<>(readings
                .findBySensorIdOrderByMeasuredAtDescIdDesc(s.getId(), PageRequest.of(0, 20)).stream()
                .map(r -> new Forecast.Point(r.getMeasuredAt(), r.getVolume())).toList());
        Collections.reverse(recent);
        Double eta = status == SensorStatus.OFFLINE ? null : Forecast.hoursToLimit(recent, s.getLimit());
        long alarms = events.countBySensorIdAndKindAndSeverityAndStartedAtGreaterThanEqual(s.getId(), EventKind.LEVEL,
                SensorStatus.ALARM, clock.instant().minus(Duration.ofDays(180)));
        int percent = s.getVolume() == null ? 0 : (int) Math.min(100, Math.round(s.getVolume() / s.getCapacity() * 100));
        int limitPercent = (int) Math.round(s.getLimit() / s.getCapacity() * 100);
        return new SensorRow(s.getId(), s.getPrefix(), s.getName(), s.getEquipmentType(), s.getNeighborhood(),
                s.getAddress(), status.apiValue(), s.getReason(), s.getVolume(), s.getLimit(), s.getCapacity(), percent,
                limitPercent, s.getLastReadingAt(), eta, alarms, s.getRadiusMeters(), s.getExpectedIntervalMinutes());
    }

    private static EventRow eventRow(SensorEvent e) {
        return new EventRow(e.getId(), e.getSensor().getPrefix(), e.getKind().name().toLowerCase(Locale.ROOT),
                e.getSeverity().apiValue(), e.getStartedAt(), e.getEndedAt(), e.getMessage());
    }

    private static int severityOrder(SensorRow row) {
        return switch (row.status()) {
            case "alarm" -> 0;
            case "warning" -> 1;
            case "offline" -> 2;
            default -> 3;
        };
    }

    private static long count(List<SensorRow> rows, String status) {
        return rows.stream().filter(r -> status.equals(r.status())).count();
    }

    static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        if (hours >= 48) {
            return duration.toDays() + " dias";
        }
        if (hours >= 1) {
            return hours + " h " + duration.toMinutesPart() + " min";
        }
        return Math.max(1, duration.toMinutes()) + " min";
    }

    /** Agrupa os chamados abertos por categoria (gráfico da visão geral). */
    @Transactional(readOnly = true)
    public Map<String, Long> openByCategory() {
        Map<String, Long> result = new LinkedHashMap<>();
        reports.findByDeletedAtIsNullAndStatusNot(ReportStatus.CLOSED).stream()
                .sorted(Comparator.comparing(Report::getCategory))
                .forEach(r -> result.merge(r.getCategory(), 1L, Long::sum));
        return result;
    }
}
