package br.com.saneia.api.report;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.saneia.api.auth.CurrentUser;
import br.com.saneia.api.common.PageResponses;

/**
 * Relatos (protocolos). O app envia JSON quando há só texto e multipart/form-data com a parte textual "report"
 * (JSON) e a parte binária "photo" quando há foto.
 */
@RestController
@RequestMapping("/reports")
@Tag(name = "Relatos", description = "Relatos dos moradores e atendimento da operadora")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    @Operation(summary = "Morador: próprios relatos. Operadora: fila completa", description = "Paginado: page e size.")
    public ResponseEntity<List<ReportResponse>> list(Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        return PageResponses.of(reports.list(CurrentUser.from(auth), request), Function.identity());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe do protocolo, com complementos e avaliação (operadora)")
    public ReportResponse get(Authentication auth, @PathVariable Long id) {
        return reports.get(CurrentUser.from(auth), id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abre um relato (JSON) e gera o protocolo no servidor",
            description = "Retorna 409 com code REPORT_COOLDOWN quando o intervalo de 20 dias ainda não passou.")
    public ReportResponse create(Authentication auth, @Valid @RequestBody ReportInput input) {
        return reports.create(CurrentUser.from(auth), input, null);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abre um relato com foto (multipart: report = JSON, photo = imagem)")
    public ReportResponse createWithPhoto(Authentication auth,
            @Parameter(description = "ReportInput em JSON") @RequestPart("report") String report,
            @RequestPart(name = "photo", required = false) MultipartFile photo) {
        return reports.create(CurrentUser.from(auth), reports.parsePart(report, ReportInput.class), photo);
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Edita o próprio relato enquanto está em received (mantém a foto anterior)")
    public ReportResponse update(Authentication auth, @PathVariable Long id, @Valid @RequestBody ReportInput input) {
        return reports.update(CurrentUser.from(auth), id, input, null);
    }

    @PatchMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Edita o próprio relato trocando a foto (multipart)")
    public ReportResponse updateWithPhoto(Authentication auth, @PathVariable Long id,
            @RequestPart("report") String report,
            @RequestPart(name = "photo", required = false) MultipartFile photo) {
        return reports.update(CurrentUser.from(auth), id, reports.parsePart(report, ReportInput.class), photo);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui (logicamente) o próprio relato em received")
    public void delete(Authentication auth, @PathVariable Long id) {
        reports.delete(CurrentUser.from(auth), id);
    }

    @PatchMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Operadora: received → in_progress → closed (com desfecho) ou só a nota de andamento")
    public ReportResponse updateStatus(Authentication auth, @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {
        return reports.updateStatus(CurrentUser.from(auth), id, request);
    }

    @PostMapping(path = "/{id}/updates", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Morador: complemento de texto em um protocolo aberto")
    public ReportResponse addUpdate(Authentication auth, @PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        return reports.addUpdate(CurrentUser.from(auth), id, request.note(), null);
    }

    @PostMapping(path = "/{id}/updates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Morador: complemento com foto (multipart: report = {\"note\"}, photo = imagem)")
    public ReportResponse addUpdateWithPhoto(Authentication auth, @PathVariable Long id,
            @RequestPart(name = "report", required = false) String report,
            @RequestPart(name = "photo", required = false) MultipartFile photo) {
        NoteRequest request = report == null || report.isBlank() ? new NoteRequest(null)
                : reports.parsePart(report, NoteRequest.class);
        return reports.addUpdate(CurrentUser.from(auth), id, request.note(), photo);
    }

    @PostMapping(path = "/{id}/assessment", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Operadora: solicita avaliação técnica (motivo de 1 a 600 caracteres)")
    public ReportResponse requestAssessment(Authentication auth, @PathVariable Long id,
            @Valid @RequestBody NoteRequest request) {
        return reports.requestAssessment(CurrentUser.from(auth), id, request.note());
    }
}
