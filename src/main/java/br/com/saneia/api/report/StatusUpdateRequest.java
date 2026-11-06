package br.com.saneia.api.report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** outcome é opcional: ao encerrar sem informar, o desfecho é "resolved". */
public record StatusUpdateRequest(
        @NotNull(message = "Informe o status.") ReportStatus status,
        @Size(max = 600, message = "O desfecho deve ter até 600 caracteres.") String resolution,
        @Size(max = 600, message = "A nota de andamento deve ter até 600 caracteres.") String progressNote,
        ReportOutcome outcome) {

    public StatusUpdateRequest(ReportStatus status, String resolution, String progressNote) {
        this(status, resolution, progressNote, null);
    }
}
