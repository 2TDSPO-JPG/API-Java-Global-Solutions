package br.com.saneia.api.insight;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import br.com.saneia.api.common.ApiException;
import br.com.saneia.api.sensor.Sensor;

@Component
public class DotNetTelemetryClient {

    private static final Logger log = LoggerFactory.getLogger(DotNetTelemetryClient.class);
    private final RestClient client;

    public DotNetTelemetryClient(@Value("${saneia.integration.dotnet-url}") String baseUrl,
            @Value("${saneia.integration.key}") String integrationKey) {
        if (!StringUtils.hasText(baseUrl)) {
            this.client = null;
            return;
        }
        URI base = URI.create(baseUrl.trim());
        if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
                || base.getFragment() != null || !("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || integrationKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Configure DOTNET_TELEMETRY_URL e SANEIA_INTEGRATION_KEY para os insights.");
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder().baseUrl(base.toString().replaceAll("/+$", ""))
                .defaultHeader("X-Saneia-Integration-Key", integrationKey)
                .requestFactory(requests).build();
    }

    boolean enabled() {
        return client != null;
    }

    InsightIndicators indicators(Sensor sensor, String period, Instant from, Instant to) {
        try {
            Summary stats = Objects.requireNonNull(client).get()
                    .uri(uri -> uri.path("/internal/telemetry/sensors/{id}/summary")
                            .queryParam("from", from).queryParam("to", to)
                            .queryParam("intervalMinutes", sensor.getExpectedIntervalMinutes())
                            .queryParam("limit", sensor.getLimit()).build(sensor.getId()))
                    .retrieve().body(Summary.class);
            if (stats != null && stats.currentStatus() != null
                    && (stats.readings() == 0 || stats.maxVolume() != null)) {
                return new InsightIndicators(String.valueOf(sensor.getId()), sensor.getPrefix(), sensor.getName(),
                        period, Instant.EPOCH.equals(from) ? null : from, to, stats.readings(), stats.firstVolume(),
                        stats.lastVolume(), stats.minVolume(), stats.maxVolume(), stats.maxVolumeAt(),
                        stats.averageVolume(), stats.changeLiters(), sensor.getCapacity(), sensor.getLimit(),
                        stats.readingsAtOrAboveLimit(), stats.alarmEvents(), null, stats.longestAlarmHours(),
                        null, null, stats.etaHours(), stats.currentStatus(), stats.lastReadingAt());
            }
        } catch (RestClientException ex) {
            log.warn("Telemetria .NET indisponível para o equipamento {} ({})", sensor.getId(),
                    ex.getClass().getSimpleName());
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Histórico de telemetria indisponível. Tente novamente.");
    }

    private record Summary(int readings, Double firstVolume, Double lastVolume, Double minVolume, Double maxVolume,
            Instant maxVolumeAt, Double averageVolume, Double changeLiters, int readingsAtOrAboveLimit,
            int alarmEvents, Double longestAlarmHours, Double etaHours, String currentStatus, Instant lastReadingAt) {
    }
}
