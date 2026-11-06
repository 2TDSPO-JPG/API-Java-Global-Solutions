package br.com.saneia.api.common;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resposta de erro padronizada. O app lê "message" (e "code"/"existingReportId" no cooldown);
 * os demais campos ajudam no Swagger e na depuração.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String message,
        String code,
        int status,
        String path,
        Instant timestamp,
        List<FieldError> errors,
        String existingReportId,
        String nextEligibleAt) {

    public record FieldError(String field, String message) {
    }
}
