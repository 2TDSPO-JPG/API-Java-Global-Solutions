package br.com.saneia.api.report;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.saneia.api.auth.CurrentUser;
import br.com.saneia.api.common.ApiException;
import br.com.saneia.api.common.Coordinate;
import br.com.saneia.api.common.Geo;
import br.com.saneia.api.file.FileService;
import br.com.saneia.api.file.StoredFile;
import br.com.saneia.api.messaging.EventPublisher;
import br.com.saneia.api.messaging.Events;
import br.com.saneia.api.sensor.Sensor;
import br.com.saneia.api.sensor.SensorService;
import br.com.saneia.api.user.AppUser;
import br.com.saneia.api.user.AppUserRepository;
import br.com.saneia.api.user.Role;

/** Regras dos relatos: propriedade, papéis, protocolo, recorrência, fotos, complementos e atendimento. */
@Service
public class ReportService {

    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter RELEASE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final DateTimeFormatter PROTOCOL_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final String PROTOCOL_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int NOTE_LIMIT = 600;

    private final ReportRepository reports;
    private final AppUserRepository users;
    private final SensorService sensors;
    private final FileService files;
    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ReportService(ReportRepository reports, AppUserRepository users, SensorService sensors, FileService files,
            EventPublisher publisher, ObjectMapper objectMapper, Validator validator, Clock clock) {
        this.reports = reports;
        this.users = users;
        this.sensors = sensors;
        this.files = files;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public Page<ReportResponse> list(CurrentUser user, Pageable pageable) {
        Page<Report> page = switch (user.role()) {
            case RESIDENT -> reports.findVisibleByOwner(user.id(), ReportStatus.CLOSED, pageable);
            case OPERATOR -> reports.findQueue(ReportStatus.CLOSED, pageable);
            case ADMIN -> throw ApiException.forbidden("Os relatos são acompanhados por moradores e pela operadora.");
        };
        return page.map(report -> toResponse(report, user));
    }

    @Transactional(readOnly = true)
    public ReportResponse get(CurrentUser user, Long id) {
        return toResponse(visible(user, id), user);
    }

    // ---------------------------------------------------------------- morador

    @Transactional
    public ReportResponse create(CurrentUser user, ReportInput input, MultipartFile photo) {
        requireRole(user, Role.RESIDENT, "Somente moradores abrem relatos.");
        String category = validCategory(input.category());
        Coordinate coordinate = input.coordinate();
        // Trava o morador: a checagem dos 20 dias e a criação acontecem na mesma transação.
        AppUser owner = users.lockById(user.id()).orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));
        Instant now = clock.instant();

        List<Report> own = reports.findByOwnerIdAndCategoryAndStatusNot(owner.getId(), category, ReportStatus.CLOSED);
        ReportPolicy.Eligibility eligibility = ReportPolicy.eligibility(own, category, coordinate.latitude(),
                coordinate.longitude(), now);
        if (!eligibility.allowed()) {
            Map<String, Object> details = new HashMap<>();
            details.put("nextEligibleAt", eligibility.nextEligibleAt().toString());
            if (eligibility.existingOpen() != null) {
                details.put("existingReportId", String.valueOf(eligibility.existingOpen().getId()));
            }
            throw new ApiException(HttpStatus.CONFLICT, cooldownMessage(eligibility.nextEligibleAt()),
                    "REPORT_COOLDOWN", details);
        }

        Report report = new Report(newProtocol(now), owner, now);
        report.applyContent(category, input.description().trim(), input.address().trim(), input.neighborhood().trim(),
                coordinate.latitude(), coordinate.longitude(), now);
        // Passado o prazo, vincula o protocolo anterior ainda aberto (prioridade por recorrência).
        report.linkPrevious(eligibility.existingOpen());
        report.linkSensor(sensors.nearestCovering(coordinate.latitude(), coordinate.longitude()).orElse(null));
        report.replacePhoto(files.storePhoto(photo));
        Report saved = reports.save(report);

        publisher.publish(Events.KEY_REPORT_OPENED, new Events.ReportOpened(saved.getId(), saved.getProtocol(),
                owner.getId(), category, saved.getNeighborhood(), saved.getPreviousReport() != null, now));
        return toResponse(saved, user);
    }

    @Transactional
    public ReportResponse update(CurrentUser user, Long id, ReportInput input, MultipartFile photo) {
        requireRole(user, Role.RESIDENT, "Somente o morador edita o próprio relato.");
        Report report = visible(user, id);
        if (report.getStatus() != ReportStatus.RECEIVED) {
            throw ApiException.conflict("O relato já está em atendimento.");
        }
        String category = validCategory(input.category());
        Coordinate coordinate = input.coordinate();
        boolean changedOccurrence = !category.equals(report.getCategory()) || Geo.distanceMeters(
                report.getLatitude(), report.getLongitude(), coordinate.latitude(), coordinate.longitude())
                > ReportPolicy.RADIUS_METERS;
        Instant now = clock.instant();
        report.applyContent(category, input.description().trim(), input.address().trim(), input.neighborhood().trim(),
                coordinate.latitude(), coordinate.longitude(), now);
        if (changedOccurrence) {
            List<Report> own = reports.findByOwnerIdAndCategoryAndStatusNot(report.getOwner().getId(), category,
                    ReportStatus.CLOSED);
            report.linkPrevious(ReportPolicy.previousOpen(own, category, coordinate.latitude(), coordinate.longitude(),
                    report.getCreatedAt()).filter(previous -> !previous.getId().equals(report.getId())).orElse(null));
        }
        report.linkSensor(sensors.nearestCovering(coordinate.latitude(), coordinate.longitude()).orElse(null));
        StoredFile newPhoto = files.storePhoto(photo);
        if (newPhoto != null) {
            report.replacePhoto(newPhoto); // sem nova foto, mantém a anterior
        }
        return toResponse(report, user);
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        requireRole(user, Role.RESIDENT, "Somente o morador exclui o próprio relato.");
        Report report = visible(user, id);
        if (report.getStatus() != ReportStatus.RECEIVED) {
            throw ApiException.conflict("O relato não pode mais ser excluído.");
        }
        report.markDeleted(clock.instant());
    }

    @Transactional
    public ReportResponse addUpdate(CurrentUser user, Long id, String note, MultipartFile photo) {
        Report report = visible(user, id);
        if (!user.is(Role.RESIDENT) || !report.isOpen()) {
            throw ApiException.conflict("O complemento só pode ser enviado pelo morador em um protocolo em aberto.");
        }
        String detail = note == null ? "" : note.trim();
        boolean hasPhoto = photo != null && !photo.isEmpty();
        if ((detail.isEmpty() && !hasPhoto) || detail.length() > NOTE_LIMIT) {
            throw ApiException.badRequest("Inclua uma foto ou descreva a atualização em até 600 caracteres.");
        }
        AppUser author = report.getOwner();
        report.addUpdate(new ReportUpdate(report, author, detail.isEmpty() ? null : detail, files.storePhoto(photo),
                clock.instant()));
        reports.flush();
        return toResponse(report, user);
    }

    // ---------------------------------------------------------------- operadora

    @Transactional
    public ReportResponse updateStatus(CurrentUser user, Long id, StatusUpdateRequest request) {
        requireRole(user, Role.OPERATOR, "O atendimento é atualizado pela operadora.");
        Report report = visible(user, id);
        applyStatus(report, request);
        return toResponse(report, user);
    }

    /** Usado pelo painel administrativo (Thymeleaf): o admin pode conduzir o atendimento de qualquer chamado. */
    @Transactional
    public void updateStatusFromPanel(Long id, StatusUpdateRequest request) {
        Report report = reports.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Relato não encontrado."));
        applyStatus(report, request);
    }

    private void applyStatus(Report report, StatusUpdateRequest request) {
        if (!report.isOpen()) {
            throw ApiException.conflict("Este atendimento já foi encerrado.");
        }
        ReportStatus next = request.status();
        if (!report.getStatus().canMoveTo(next)) {
            throw ApiException.conflict("Mudança de status inválida: siga Recebido → Em atendimento → Encerrado.");
        }
        String resolution = trimToNull(request.resolution());
        String progressNote = trimToNull(request.progressNote());
        if (next == ReportStatus.CLOSED && resolution == null) {
            throw ApiException.badRequest("Informe o desfecho antes de encerrar o atendimento.");
        }
        ReportStatus previous = report.getStatus();
        report.changeStatus(next, resolution, progressNote, clock.instant());
        if (previous != next || progressNote != null) {
            publisher.publish(Events.KEY_REPORT_STATUS, new Events.ReportStatusChanged(report.getId(),
                    report.getProtocol(), report.getOwner().getId(), next.apiValue(), report.getResolution(),
                    report.getProgressNote(), clock.instant()));
        }
    }

    @Transactional
    public ReportResponse requestAssessment(CurrentUser user, Long id, String note) {
        requireRole(user, Role.OPERATOR, "A avaliação é registrada pela operadora.");
        Report report = visible(user, id);
        if (!report.isOpen()) {
            throw ApiException.conflict("Selecione um protocolo em aberto para solicitar uma avaliação.");
        }
        if (report.getAssessmentRequestedAt() != null) {
            throw ApiException.conflict("Já existe uma avaliação solicitada para este protocolo.");
        }
        String detail = note == null ? "" : note.trim();
        if (detail.isEmpty() || detail.length() > NOTE_LIMIT) {
            throw ApiException.badRequest("Informe o motivo da avaliação em até 600 caracteres.");
        }
        Instant now = clock.instant();
        report.requestAssessment(detail, now);
        publisher.publish(Events.KEY_REPORT_ASSESSMENT,
                new Events.AssessmentRequested(report.getId(), report.getProtocol(), detail, now));
        return toResponse(report, user);
    }

    // ---------------------------------------------------------------- multipart

    /** Converte e valida a parte textual "report" (JSON) enviada no multipart. */
    public <T> T parsePart(String json, Class<T> type) {
        if (!StringUtils.hasText(json)) {
            throw ApiException.badRequest("Envie os dados do relato na parte \"report\".");
        }
        T value;
        try {
            value = objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw ApiException.badRequest("Os dados do relato não estão em JSON válido.");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return value;
    }

    // ---------------------------------------------------------------- apoio

    private Report visible(CurrentUser user, Long id) {
        Report report = reports.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Relato não encontrado."));
        if (user.is(Role.OPERATOR)) {
            return report;
        }
        if (user.is(Role.RESIDENT) && report.getOwner().getId().equals(user.id())) {
            return report;
        }
        if (user.is(Role.ADMIN)) {
            throw ApiException.forbidden("Os relatos são acompanhados por moradores e pela operadora.");
        }
        throw ApiException.notFound("Relato não encontrado.");
    }

    private static void requireRole(CurrentUser user, Role role, String message) {
        if (!user.is(role)) {
            throw ApiException.forbidden(message);
        }
    }

    private static String validCategory(String category) {
        if (!ReportCategory.isValid(category)) {
            throw ApiException.badRequest("Categoria inválida. Use: " + String.join(", ", ReportCategory.ALL) + ".");
        }
        return category.trim();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    static String cooldownMessage(Instant nextEligibleAt) {
        return "Um novo protocolo para este problema e local poderá ser aberto em "
                + RELEASE_FORMAT.format(nextEligibleAt.atZone(BRAZIL))
                + ". Você pode complementar um protocolo em aberto sem esperar.";
    }

    private String newProtocol(Instant now) {
        String date = PROTOCOL_DATE.format(now.atZone(BRAZIL));
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder("SAN-").append(date).append('-');
            for (int i = 0; i < 4; i++) {
                code.append(PROTOCOL_ALPHABET.charAt(random.nextInt(PROTOCOL_ALPHABET.length())));
            }
            String protocol = code.toString();
            if (!reports.existsByProtocol(protocol)) {
                return protocol;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um protocolo único.");
    }

    ReportResponse toResponse(Report report, CurrentUser viewer) {
        boolean operator = viewer.is(Role.OPERATOR);
        Sensor sensor = report.getSensor();
        Report previous = report.getPreviousReport();
        List<ReportResponse.Update> updates = report.getUpdates().stream()
                .map(update -> new ReportResponse.Update(String.valueOf(update.getId()), update.getCreatedAt(),
                        update.getNote() == null ? "" : update.getNote(),
                        update.getPhoto() == null ? null : files.signedUrl(update.getPhoto().getId())))
                .toList();
        ReportResponse.Assessment assessment = operator && report.getAssessmentRequestedAt() != null
                ? new ReportResponse.Assessment(report.getAssessmentRequestedAt(), report.getAssessmentNote())
                : null;
        return new ReportResponse(
                String.valueOf(report.getId()), report.getProtocol(), report.getStatus(), report.getCreatedAt(),
                report.getCategory(), report.getDescription(), report.getAddress(), report.getNeighborhood(),
                new Coordinate(report.getLatitude(), report.getLongitude()),
                report.getPhoto() == null ? null : files.signedUrl(report.getPhoto().getId()),
                sensor == null ? null : String.valueOf(sensor.getId()),
                report.getResolution(), report.getProgressNote(),
                previous == null ? null : String.valueOf(previous.getId()),
                previous == null ? null : previous.getProtocol(),
                operator ? report.getOwner().getName() : null,
                report.getClosedAt(), updates, assessment);
    }
}
