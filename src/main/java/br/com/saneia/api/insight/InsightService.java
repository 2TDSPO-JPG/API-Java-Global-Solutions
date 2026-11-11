package br.com.saneia.api.insight;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.saneia.api.common.ApiException;
import br.com.saneia.api.sensor.EventKind;
import br.com.saneia.api.sensor.Forecast;
import br.com.saneia.api.sensor.Sensor;
import br.com.saneia.api.sensor.SensorEvent;
import br.com.saneia.api.sensor.SensorEventRepository;
import br.com.saneia.api.sensor.SensorReading;
import br.com.saneia.api.sensor.SensorReadingRepository;
import br.com.saneia.api.sensor.SensorService;
import br.com.saneia.api.sensor.SensorStatus;

/**
 * Funcionalidade de IA generativa integrada ao fluxo da operadora (requisito 04).
 * 1) busca as medições e eventos do período no banco; 2) calcula os indicadores (fonte dos números);
 * 3) envia só esses dados ao LLM via Spring AI para um resumo em português; 4) devolve texto + indicadores.
 * O modelo não define alarmes nem substitui as medições.
 */
@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);
    private static final int MAX_READINGS = 5000;
    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    static final String SYSTEM_PROMPT = """
            Você é o assistente da operadora de saneamento do Saneia. Escreva em português do Brasil, em até 5 frases curtas,
            um resumo do período para quem decide onde agir. Regras:
            - Use somente os números do JSON recebido; não invente valores, datas nem causas.
            - Destaque: tendência do volume, maior volume, leituras no limite, alarmes e intervalos sem comunicação.
            - Se etaHours existir, diga que, mantido o ritmo atual, o reservatório pode atingir o limite em cerca de N horas.
            - Se houver poucas leituras (menos de 3) ou dados desatualizados, diga que a estimativa é limitada.
            - Não diga que o problema foi resolvido nem dê ordens; sugira no máximo uma verificação prática.
            - Não use markdown, listas nem emojis.
            """;

    private final SensorService sensors;
    private final SensorReadingRepository readings;
    private final SensorEventRepository events;
    private final DotNetTelemetryClient dotNetTelemetry;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String apiKey;
    private final String model;

    public InsightService(SensorService sensors, SensorReadingRepository readings, SensorEventRepository events,
            DotNetTelemetryClient dotNetTelemetry, ObjectProvider<ChatClient.Builder> chatClientBuilder,
            ObjectMapper objectMapper, Clock clock,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:}") String model) {
        this.sensors = sensors;
        this.readings = readings;
        this.events = events;
        this.dotNetTelemetry = dotNetTelemetry;
        this.chatClientBuilder = chatClientBuilder;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.apiKey = apiKey;
        this.model = model;
    }

    /** Sem transação aberta durante a chamada ao LLM: cada consulta ao banco usa a própria transação curta. */
    public InsightResponse summarize(Long sensorId, InsightRequest request) {
        Sensor sensor = sensors.requireActive(sensorId);
        InsightIndicators indicators = indicators(sensor, request);
        String telemetrySource = dotNetTelemetry.enabled() ? "dotnet" : "java";
        if (indicators.readings() == 0) {
            return new InsightResponse("Não há leituras deste equipamento no período selecionado. "
                    + "Escolha um período maior ou verifique a comunicação do dispositivo.", false, null,
                    indicators, telemetrySource);
        }
        if (llmEnabled()) {
            try {
                String text = chatClientBuilder.getObject().build()
                        .prompt()
                        .system(SYSTEM_PROMPT)
                        .user("Dados calculados do equipamento (JSON):\n" + objectMapper.writeValueAsString(indicators))
                        .call()
                        .content();
                if (StringUtils.hasText(text)) {
                    return new InsightResponse(text.trim(), true, model, indicators, telemetrySource);
                }
            } catch (JsonProcessingException ex) {
                log.warn("Falha ao serializar os indicadores", ex);
            } catch (RuntimeException ex) {
                log.warn("LLM indisponível para o sensor {}: {}", sensorId, ex.getMessage());
            }
        }
        return new InsightResponse(fallbackSummary(indicators), false, null, indicators, telemetrySource);
    }

    boolean llmEnabled() {
        return StringUtils.hasText(apiKey) && !"desativado".equalsIgnoreCase(apiKey.trim())
                && chatClientBuilder.getIfAvailable() != null;
    }

    InsightIndicators indicators(Sensor sensor, InsightRequest request) {
        Instant now = clock.instant();
        Instant to = request.to() == null || request.to().isAfter(now) ? now : request.to();
        Instant from = "all".equals(request.period()) || request.from() == null
                ? ("all".equals(request.period()) ? Instant.EPOCH : defaultFrom(request.period(), to))
                : request.from();
        if (from.isAfter(to)) {
            throw ApiException.badRequest("O início do período deve ser anterior ao fim.");
        }
        if (dotNetTelemetry.enabled()) {
            return dotNetTelemetry.indicators(sensor, request.period(), from, to);
        }

        List<SensorReading> series = readings.findInRange(sensor.getId(), from, to, PageRequest.of(0, MAX_READINGS));
        List<SensorEvent> overlapping = events.findOverlapping(sensor.getId(), from, to);

        int alarms = 0;
        int warnings = 0;
        int offline = 0;
        double longestAlarm = 0;
        double offlineHours = 0;
        for (SensorEvent event : overlapping) {
            Instant end = event.getEndedAt() == null ? to : event.getEndedAt();
            double hours = Duration.between(event.getStartedAt().isBefore(from) ? from : event.getStartedAt(),
                    end.isAfter(to) ? to : end).toMinutes() / 60d;
            if (event.getKind() == EventKind.COMMUNICATION) {
                offline++;
                offlineHours += Math.max(0, hours);
            } else if (event.getSeverity() == SensorStatus.ALARM) {
                alarms++;
                longestAlarm = Math.max(longestAlarm, hours);
            } else {
                warnings++;
            }
        }

        SensorReading first = series.isEmpty() ? null : series.get(0);
        SensorReading last = series.isEmpty() ? null : series.get(series.size() - 1);
        SensorReading peak = series.stream()
                .max((a, b) -> Double.compare(a.getVolume(), b.getVolume())).orElse(null);
        Double eta = series.isEmpty() ? null : Forecast.hoursToLimit(
                series.stream().map(r -> new Forecast.Point(r.getMeasuredAt(), r.getVolume())).toList(),
                sensor.getLimit());

        return new InsightIndicators(
                String.valueOf(sensor.getId()), sensor.getPrefix(), sensor.getName(), request.period(),
                Instant.EPOCH.equals(from) ? null : from, to, series.size(),
                first == null ? null : first.getVolume(), last == null ? null : last.getVolume(),
                stat(series, SensorReading::getVolume, true), peak == null ? null : peak.getVolume(),
                peak == null ? null : peak.getMeasuredAt(), average(series),
                first == null ? null : round(last.getVolume() - first.getVolume()),
                sensor.getCapacity(), sensor.getLimit(),
                (int) series.stream().filter(r -> r.getVolume() >= r.getLimit()).count(),
                alarms, warnings, alarms == 0 ? null : round(longestAlarm), offline,
                offline == 0 ? null : round(offlineHours), eta,
                sensors.effectiveStatus(sensor).apiValue(), sensor.getLastReadingAt());
    }

    /** Resumo calculado (sem LLM), com os mesmos indicadores. */
    static String fallbackSummary(InsightIndicators i) {
        StringBuilder text = new StringBuilder();
        text.append(String.format(PT_BR, "%s registrou %d leituras no período", i.name(), i.readings()));
        if (i.changeLiters() != null && i.readings() >= 2) {
            text.append(i.changeLiters() == 0 ? ", com volume estável"
                    : String.format(PT_BR, ", com %s de %.0f L", i.changeLiters() > 0 ? "alta" : "queda",
                            Math.abs(i.changeLiters())));
        }
        text.append(String.format(PT_BR, ". O maior volume foi %.0f L", i.maxVolume()));
        if (i.maxVolumeAt() != null) {
            text.append(" em ").append(DATE_TIME.format(i.maxVolumeAt().atZone(BRAZIL)));
        }
        text.append(String.format(PT_BR, " (limite de %.0f L). ", i.limit()));
        if (i.alarmEvents() > 0) {
            text.append(String.format(PT_BR, "Houve %d alarme(s) de nível; o mais longo durou cerca de %.1f h. ",
                    i.alarmEvents(), i.longestAlarmHours()));
        } else {
            text.append("Não houve alarme de nível. ");
        }
        if (i.offlineEvents() != null && i.offlineEvents() > 0) {
            text.append(String.format(PT_BR, "O equipamento ficou sem comunicação %d vez(es), somando %.1f h. ",
                    i.offlineEvents(), i.offlineHours()));
        }
        if (i.etaHours() != null) {
            text.append(String.format(PT_BR,
                    "Se o ritmo atual continuar, o reservatório pode atingir o limite em cerca de %.1f horas.",
                    i.etaHours()));
        } else if (i.readings() < Forecast.MIN_POINTS) {
            text.append("A estimativa até o limite está indisponível: são necessárias mais leituras.");
        } else {
            text.append("Sem tendência de subida suficiente para estimar quando o limite seria atingido.");
        }
        return text.toString().trim().replace("  ", " ");
    }

    private static Instant defaultFrom(String period, Instant to) {
        return switch (period) {
            case "today" -> to.atZone(BRAZIL).toLocalDate().atStartOfDay(BRAZIL).toInstant();
            case "7d" -> to.minus(Duration.ofDays(7));
            case "30d" -> to.minus(Duration.ofDays(30));
            case "90d" -> to.minus(Duration.ofDays(90));
            default -> Instant.EPOCH;
        };
    }

    private static Double stat(List<SensorReading> series, ToDoubleFunction<SensorReading> field, boolean min) {
        if (series.isEmpty()) {
            return null;
        }
        return min ? series.stream().mapToDouble(field).min().orElse(0)
                : series.stream().mapToDouble(field).max().orElse(0);
    }

    private static Double average(List<SensorReading> series) {
        return series.isEmpty() ? null : round(series.stream().mapToDouble(SensorReading::getVolume).average().orElse(0));
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
