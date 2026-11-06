package br.com.saneia.api.report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotNull(message = "Informe o status.") ReportStatus status,
        @Size(max = 600, message = "O desfecho deve ter até 600 caracteres.") String resolution,
        @Size(max = 600, message = "A nota de andamento deve ter até 600 caracteres.") String progressNote) {
}
