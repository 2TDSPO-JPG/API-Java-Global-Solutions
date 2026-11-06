package br.com.saneia.api.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Parâmetros do Saneia lidos de application.yml e de variáveis de ambiente. */
@ConfigurationProperties(prefix = "saneia")
public record SaneiaProperties(
        @DefaultValue("http://localhost:8080/api") String publicUrl,
        Mail mail,
        Security security,
        Files files,
        Messaging messaging,
        Telemetry telemetry,
        Seed seed) {

    public record Security(
            String jwtSecret,
            @DefaultValue("PT12H") Duration tokenTtl,
            @DefaultValue("*") List<String> corsOrigins) {
    }

    /** E-mail de alerta de risco máximo (o SMTP vem de spring.mail.*). */
    public record Mail(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("Saneia Alertas <no-reply@saneia.app>") String from,
            List<String> recipients) {
    }

    public record Files(String signingSecret, @DefaultValue("PT2H") Duration urlTtl) {
    }

    public record Messaging(@DefaultValue("rabbit") String mode) {
        public boolean rabbit() {
            return "rabbit".equalsIgnoreCase(mode);
        }
    }

    public record Telemetry(
            String deviceKey,
            @DefaultValue("30") int defaultExpectedIntervalMinutes,
            @DefaultValue("0.85") double warningRatio,
            @DefaultValue("12") double forecastWindowHours,
            @DefaultValue("2") int stableReadingsToNormalize,
            @DefaultValue("PT1M") Duration offlineCheck) {
    }

    public record Seed(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("true") boolean demoData,
            String adminEmail,
            String adminPassword,
            String operatorEmail,
            String operatorPassword,
            String residentEmail,
            String residentPassword) {
    }
}
