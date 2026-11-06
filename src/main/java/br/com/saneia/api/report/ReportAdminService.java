package br.com.saneia.api.report;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import br.com.saneia.api.common.ApiException;
import br.com.saneia.api.messaging.Events;
import br.com.saneia.api.sensor.Sensor;
import br.com.saneia.api.sensor.SensorRepository;
import br.com.saneia.api.sensor.SensorService;
import br.com.saneia.api.user.AppUser;
import br.com.saneia.api.user.AppUserRepository;
import br.com.saneia.api.user.Role;

/**
 * Gestão avançada dos chamados pela equipe (painel): edição completa, prioridade/SLA, responsável, vínculo
 * manual com sensor, notas internas, ações em lote e reabertura automática dos chamados "estabilizados".
 */
@Service
public class ReportAdminService {

    private static final Logger log = LoggerFactory.getLogger(ReportAdminService.class);
    /** Janela de observação de um chamado encerrado como "Estabilizado". */
    public static final Duration OBSERVATION_WINDOW = Duration.ofDays(7);

    private final ReportRepository reports;
    private final ReportService reportService;
    private final ReportHistoryService history;
    private final SensorRepository sensors;
    private final SensorService sensorService;
    private final AppUserRepository users;
    private final Clock clock;

    public ReportAdminService(ReportRepository reports, ReportService reportService, ReportHistoryService history,
            SensorRepository sensors, SensorService sensorService, AppUserRepository users, Clock clock) {
        this.reports = reports;
        this.reportService = reportService;
        this.history = history;
        this.sensors = sensors;
        this.sensorService = sensorService;
        this.users = users;
        this.clock = clock;
    }

    /** Dados editáveis pela equipe. sensorMode: "auto" (cobertura), "none" (sem vínculo) ou o id do sensor. */
    public record Edit(String category, String description, String address, String neighborhood,
            ReportPriority priority, Long assigneeId, String sensorMode) {
    }

    public record BatchResult(int changed, List<String> errors) {
    }

    @Transactional
    public void edit(Long id, Edit edit, String actor) {
        Report report = find(id);
        Instant now = clock.instant();
        List<String> changes = new ArrayList<>();

        String category = edit.category() == null ? report.getCategory() : edit.category().trim();
        if (!ReportCategory.isValid(category)) {
            throw ApiException.badRequest("Categoria inválida.");
        }
        String description = required(edit.description(), report.getDescription(), 1000, "descrição");
        String address = required(edit.address(), report.getAddress(), 200, "endereço");
        String neighborhood = required(edit.neighborhood(), report.getNeighborhood(), 80, "bairro");
        if (!category.equals(report.getCategory())) {
            changes.add("categoria (" + report.getCategory() + " → " + category + ")");
        }
        if (!description.equals(report.getDescription())) {
            changes.add("descrição");
        }
        if (!address.equals(report.getAddress())) {
            changes.add("endereço");
        }
        if (!neighborhood.equals(report.getNeighborhood())) {
            changes.add("bairro (" + report.getNeighborhood() + " → " + neighborhood + ")");
        }
        if (!changes.isEmpty()) {
            report.applyContent(category, description, address, neighborhood, report.getLatitude(),
                    report.getLongitude(), now);
            history.record(report, ReportHistory.Type.EDITED, actor, null, null,
                    "Campos alterados: " + String.join(", ", changes) + ".");
        }

        if (edit.priority() != null && edit.priority() != report.getPriority()) {
            ReportPriority before = report.getPriority();
            report.prioritize(edit.priority(), now);
            history.record(report, ReportHistory.Type.PRIORITY, actor, before.getLabel(), edit.priority().getLabel(),
                    "Prioridade alterada; novo prazo calculado pelo SLA.");
        }

        AppUser currentAssignee = report.getAssignee();
        Long currentAssigneeId = currentAssignee == null ? null : currentAssignee.getId();
        if (!Objects.equals(currentAssigneeId, edit.assigneeId())) {
            AppUser assignee = edit.assigneeId() == null ? null : staff(edit.assigneeId());
            report.assign(assignee, now);
            history.record(report, ReportHistory.Type.ASSIGNED, actor,
                    currentAssignee == null ? "ninguém" : currentAssignee.getName(),
                    assignee == null ? "ninguém" : assignee.getName(), null);
        }

        if (StringUtils.hasText(edit.sensorMode())) {
            applySensor(report, edit.sensorMode().trim(), actor);
        }
    }

    @Transactional
    public void addInternalNote(Long id, String note, String actor) {
        Report report = find(id);
        String text = note == null ? "" : note.trim();
        if (text.isEmpty() || text.length() > 1000) {
            throw ApiException.badRequest("Escreva a nota interna em até 1000 caracteres.");
        }
        history.internalNote(report, actor, text);
    }

    /**
     * Ações em lote: start (iniciar), close (encerrar), assign (atribuir) e priority (prioridade).
     * As validações acontecem antes de chamar o ReportService para que um item inválido não marque a
     * transação inteira para rollback: ele só entra na lista de erros.
     */
    @Transactional
    public BatchResult batch(List<Long> ids, String action, String resolution, ReportOutcome outcome,
            Long assigneeId, ReportPriority priority, String actor) {
        if (ids == null || ids.isEmpty()) {
            throw ApiException.badRequest("Selecione pelo menos um chamado.");
        }
        int changed = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                Report report = find(id);
                switch (action == null ? "" : action) {
                    case "start" -> {
                        if (report.getStatus() != ReportStatus.RECEIVED) {
                            throw ApiException.conflict("já está em atendimento ou encerrado");
                        }
                        reportService.updateStatusFromPanel(id, new StatusUpdateRequest(ReportStatus.IN_PROGRESS,
                                null, "Atendimento iniciado (ação em lote)."), actor);
                    }
                    case "close" -> {
                        if (!report.isOpen()) {
                            throw ApiException.conflict("já está encerrado");
                        }
                        if (!StringUtils.hasText(resolution)) {
                            throw ApiException.badRequest("informe o desfecho");
                        }
                        if (report.getStatus() == ReportStatus.RECEIVED) {
                            reportService.updateStatusFromPanel(id, new StatusUpdateRequest(ReportStatus.IN_PROGRESS,
                                    null, "Atendimento conjunto da área."), actor);
                        }
                        reportService.updateStatusFromPanel(id, new StatusUpdateRequest(ReportStatus.CLOSED,
                                resolution, null, outcome), actor);
                    }
                    case "assign" -> edit(id, new Edit(null, null, null, null, null, assigneeId, null), actor);
                    case "priority" -> {
                        if (priority == null) {
                            throw ApiException.badRequest("escolha a prioridade");
                        }
                        edit(id, new Edit(null, null, null, null, priority, currentAssignee(report), null), actor);
                    }
                    default -> throw ApiException.badRequest("ação inválida");
                }
                changed++;
            } catch (ApiException ex) {
                errors.add(protocolOf(id) + ": " + ex.getMessage());
            }
        }
        return new BatchResult(changed, errors);
    }

    /**
     * Chamado na chegada de um alarme: reabre os chamados vinculados ao sensor que foram encerrados como
     * "Estabilizado" há menos de 7 dias. O morador é notificado da reabertura.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int autoReopenStabilized(Events.SensorAlert alert) {
        if (!"alarm".equals(alert.status())) {
            return 0;
        }
        Instant since = clock.instant().minus(OBSERVATION_WINDOW);
        int reopened = 0;
        for (Report report : reports.findBySensorIdAndDeletedAtIsNullOrderByCreatedAtDesc(alert.sensorId())) {
            if (report.getStatus() == ReportStatus.CLOSED && report.getOutcome() == ReportOutcome.STABILIZED
                    && report.getClosedAt() != null && !report.getClosedAt().isBefore(since)) {
                reportService.reopen(report, "Reaberto automaticamente: o sensor " + alert.prefix()
                        + " voltou ao limite operacional durante a observação.", "Sistema (monitoramento)", true);
                reopened++;
                log.info("Chamado {} reaberto automaticamente pelo alarme de {}", report.getProtocol(), alert.prefix());
            }
        }
        return reopened;
    }

    private void applySensor(Report report, String mode, String actor) {
        Sensor before = report.getSensor();
        Sensor target;
        boolean locked;
        switch (mode.toLowerCase(Locale.ROOT)) {
            case "auto" -> {
                target = sensorService.nearestCovering(report.getLatitude(), report.getLongitude()).orElse(null);
                locked = false;
            }
            case "none" -> {
                target = null;
                locked = true;
            }
            default -> {
                Long sensorId;
                try {
                    sensorId = Long.valueOf(mode);
                } catch (NumberFormatException ex) {
                    throw ApiException.badRequest("Equipamento inválido.");
                }
                target = sensors.findByIdAndActiveTrue(sensorId)
                        .orElseThrow(() -> ApiException.notFound("Equipamento não encontrado."));
                locked = true;
            }
        }
        Long beforeId = before == null ? null : before.getId();
        Long targetId = target == null ? null : target.getId();
        if (Objects.equals(beforeId, targetId) && locked == report.isSensorLocked()) {
            return;
        }
        report.lockSensor(target, locked);
        history.record(report, ReportHistory.Type.SENSOR_LINK, actor,
                before == null ? "sem sensor" : before.getPrefix(),
                target == null ? "sem sensor" : target.getPrefix(),
                locked ? "Vínculo revisado manualmente pela equipe." : "Vínculo recalculado pela cobertura.");
    }

    private AppUser staff(Long userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Responsável não encontrado."));
        if (user.getRole() == Role.RESIDENT) {
            throw ApiException.badRequest("O responsável deve ser da operadora ou da administração.");
        }
        return user;
    }

    private static Long currentAssignee(Report report) {
        return report.getAssignee() == null ? null : report.getAssignee().getId();
    }

    private String protocolOf(Long id) {
        return reports.findById(id).map(Report::getProtocol).orElse("#" + id);
    }

    private Report find(Long id) {
        return reports.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> ApiException.notFound("Chamado não encontrado."));
    }

    private static String required(String value, String current, int max, String field) {
        if (value == null) {
            return current;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > max) {
            throw ApiException.badRequest("Informe o campo " + field + " (até " + max + " caracteres).");
        }
        return trimmed;
    }
}
