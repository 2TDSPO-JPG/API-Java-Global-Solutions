package br.com.saneia.api.report;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

import br.com.saneia.api.common.Geo;

/**
 * Regra de recorrência (igual a src/report-policy.js do app):
 * mesmo morador + mesma categoria + até 75 m + protocolo não encerrado nos últimos 20 dias bloqueia a abertura.
 * Protocolos encerrados não bloqueiam; excluídos continuam contando.
 */
public final class ReportPolicy {

    public static final Duration INTERVAL = Duration.ofDays(20);
    public static final double RADIUS_METERS = 75;

    private ReportPolicy() {
    }

    public record Eligibility(boolean allowed, Instant nextEligibleAt, Report existingOpen) {
    }

    public static boolean matches(Report report, String category, double latitude, double longitude) {
        return report.getCategory().equals(category)
                && Geo.distanceMeters(report.getLatitude(), report.getLongitude(), latitude, longitude) <= RADIUS_METERS;
    }

    /** Protocolo anterior ainda aberto (não excluído) que corresponde à ocorrência. */
    public static Optional<Report> previousOpen(Collection<Report> ownReports, String category, double latitude,
            double longitude, Instant before) {
        return ownReports.stream()
                .filter(r -> !r.isDeleted() && r.isOpen() && matches(r, category, latitude, longitude))
                .filter(r -> before == null || r.getCreatedAt().isBefore(before))
                .max(Comparator.comparing(Report::getCreatedAt).thenComparing(Report::getId));
    }

    public static Eligibility eligibility(Collection<Report> ownReports, String category, double latitude,
            double longitude, Instant now) {
        Optional<Report> latest = ownReports.stream()
                .filter(r -> r.isOpen() && matches(r, category, latitude, longitude))
                .max(Comparator.comparing(Report::getCreatedAt));
        Report existing = previousOpen(ownReports, category, latitude, longitude, null).orElse(null);
        if (latest.isPresent()) {
            Instant next = latest.get().getCreatedAt().plus(INTERVAL);
            if (next.isAfter(now)) {
                return new Eligibility(false, next, existing);
            }
        }
        return new Eligibility(true, null, existing);
    }
}
