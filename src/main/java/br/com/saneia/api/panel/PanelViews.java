package br.com.saneia.api.panel;

import java.time.Instant;
import java.util.List;

/** Modelos de tela do painel administrativo (dados já prontos para o Thymeleaf). */
public final class PanelViews {

    private PanelViews() {
    }

    public enum RiskLevel {
        CRITICO("Risco máximo", "critico"),
        ALTO("Alto", "alto"),
        MODERADO("Moderado", "moderado"),
        BAIXO("Baixo", "baixo");

        private final String label;
        private final String css;

        RiskLevel(String label, String css) {
            this.label = label;
            this.css = css;
        }

        public String getLabel() {
            return label;
        }

        public String getCss() {
            return css;
        }
    }

    public record Kpis(long openReports, long received, long inProgress, long closed, long recurrentOpen,
            long deleted, String averageHandling, long sensorsActive, long sensorsAlarm, long sensorsWarning,
            long sensorsOffline, long sensorsNormal, long users, long emailsSent24h, long emailsFailed24h,
            boolean smtpConfigured) {

        public long totalReports() {
            return received + inProgress + closed;
        }

        public int percent(long part) {
            long total = totalReports();
            return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
        }
    }

    public record AreaRisk(String name, RiskLevel level, String reason, int sensors, int alarms, int warnings,
            int offline, long openReports, long recurrent, List<String> alarmPrefixes) {
    }

    public record ReportRow(Long id, String protocol, String category, String neighborhood, String address,
            String status, Instant createdAt, Instant closedAt, String ownerName, String ownerEmail,
            String sensorPrefix, boolean recurrent, boolean deleted, boolean hasPhoto) {
    }

    public record UpdateRow(Instant createdAt, String note, String photoUrl) {
    }

    public record ReportDetail(ReportRow row, String description, double latitude, double longitude,
            String resolution, String progressNote, String assessmentNote, Instant assessmentRequestedAt,
            String photoUrl, String previousProtocol, Long previousId, List<UpdateRow> updates) {
    }

    public record SensorRow(Long id, String prefix, String name, String equipmentType, String neighborhood,
            String address, String status, String reason, Double volume, double limit, double capacity,
            int percent, int limitPercent, Instant lastReadingAt, Double etaHours, long alarmsSixMonths,
            double radiusMeters, int expectedIntervalMinutes) {
    }

    public record ReadingRow(Instant at, double volume, int percent, String status, String source) {
    }

    public record EventRow(Long id, String sensorPrefix, String kind, String severity, Instant startedAt,
            Instant endedAt, String message) {
    }

    public record SensorDetail(SensorRow row, double latitude, double longitude, List<ReadingRow> readings,
            List<EventRow> events, List<ReportRow> reports, int chartMax) {
    }

    public record UserRow(Long id, String name, String email, String role, Instant createdAt) {
    }

    public record Dashboard(Kpis kpis, List<AreaRisk> areas, List<ReportRow> recentReports,
            List<EventRow> recentEvents, List<SensorRow> sensorsAtRisk) {

        public long criticalAreas() {
            return areas.stream().filter(a -> a.level() == RiskLevel.CRITICO).count();
        }
    }
}
