package br.com.saneia.api.report;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.com.saneia.api.common.Coordinate;

/** Report do contrato. photoUrl, sensorId e resolution aceitam null; assessment só aparece para a operadora. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportResponse(
        String id,
        String protocol,
        @JsonInclude(JsonInclude.Include.ALWAYS) ReportStatus status,
        Instant createdAt,
        String category,
        String description,
        String address,
        String neighborhood,
        Coordinate coordinate,
        @JsonInclude(JsonInclude.Include.ALWAYS) String photoUrl,
        @JsonInclude(JsonInclude.Include.ALWAYS) String sensorId,
        @JsonInclude(JsonInclude.Include.ALWAYS) String resolution,
        String progressNote,
        @JsonInclude(JsonInclude.Include.ALWAYS) String previousReportId,
        @JsonInclude(JsonInclude.Include.ALWAYS) String previousProtocol,
        String ownerName,
        Instant closedAt,
        List<Update> updates,
        Assessment assessment) {

    public record Update(String id, Instant createdAt, String note, String photoUrl) {
    }

    public record Assessment(Instant requestedAt, String note) {
    }
}
