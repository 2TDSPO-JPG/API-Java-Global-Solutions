package br.com.saneia.api.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.saneia.api.report.Report;
import br.com.saneia.api.report.ReportPolicy;
import br.com.saneia.api.report.ReportStatus;
import br.com.saneia.api.user.AppUser;
import br.com.saneia.api.user.Role;

@DisplayName("Regra de recorrência dos relatos (20 dias, mesma categoria, até 75 m)")
class ReportPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final AppUser OWNER = new AppUser("Morador", "m@saneia.test", "hash", Role.RESIDENT, NOW);

    private static Report report(String category, double lat, double lon, Duration age) {
        Report report = new Report("SAN-T", OWNER, NOW.minus(age));
        report.applyContent(category, "descrição", "endereço", "bairro", lat, lon, NOW.minus(age));
        return report;
    }

    @Test
    @DisplayName("Bloqueia dentro de 20 dias e informa a data de liberação")
    void blocksInsideInterval() {
        Report open = report("Vazamento", -23.5, -46.6, Duration.ofDays(5));
        ReportPolicy.Eligibility result = ReportPolicy.eligibility(List.of(open), "Vazamento", -23.5002, -46.6, NOW);
        assertThat(result.allowed()).isFalse();
        assertThat(result.nextEligibleAt()).isEqualTo(open.getCreatedAt().plus(Duration.ofDays(20)));
        assertThat(result.existingOpen()).isSameAs(open);
    }

    @Test
    @DisplayName("Libera após 20 dias e indica o protocolo aberto para vínculo")
    void allowsAfterInterval() {
        Report open = report("Vazamento", -23.5, -46.6, Duration.ofDays(25));
        ReportPolicy.Eligibility result = ReportPolicy.eligibility(List.of(open), "Vazamento", -23.5, -46.6, NOW);
        assertThat(result.allowed()).isTrue();
        assertThat(result.existingOpen()).isSameAs(open);
    }

    @Test
    @DisplayName("Protocolo encerrado não bloqueia; distância acima de 75 m não conta")
    void closedOrFarDoesNotBlock() {
        Report closed = report("Vazamento", -23.5, -46.6, Duration.ofDays(2));
        closed.changeStatus(ReportStatus.CLOSED, "Resolvido", null, NOW);
        Report far = report("Vazamento", -23.51, -46.6, Duration.ofDays(2));
        ReportPolicy.Eligibility result = ReportPolicy.eligibility(List.of(closed, far), "Vazamento", -23.5, -46.6, NOW);
        assertThat(result.allowed()).isTrue();
        assertThat(result.existingOpen()).isNull();
    }

    @Test
    @DisplayName("Relato excluído continua bloqueando, mas não é oferecido para complemento")
    void deletedStillCounts() {
        Report deleted = report("Mau cheiro", -23.5, -46.6, Duration.ofDays(3));
        deleted.markDeleted(NOW);
        ReportPolicy.Eligibility result = ReportPolicy.eligibility(List.of(deleted), "Mau cheiro", -23.5, -46.6, NOW);
        assertThat(result.allowed()).isFalse();
        assertThat(result.existingOpen()).isNull();
    }
}
