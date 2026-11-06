package br.com.saneia.api.panel;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

/** Formatação usada nos templates do painel: ${@fmt.dataHora(x)}, ${@fmt.status(x)} etc. */
@Component("fmt")
public class Fmt {

    private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale PT_BR = Locale.of("pt", "BR");

    public String dataHora(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant.atZone(BRAZIL));
    }

    public String haQuanto(Instant instant) {
        if (instant == null) {
            return "sem leitura";
        }
        Duration age = Duration.between(instant, Instant.now());
        if (age.isNegative() || age.toMinutes() < 1) {
            return "agora";
        }
        if (age.toMinutes() < 60) {
            return "há " + age.toMinutes() + " min";
        }
        if (age.toHours() < 48) {
            return "há " + age.toHours() + " h";
        }
        return "há " + age.toDays() + " dias";
    }

    public String litros(Double value) {
        return value == null ? "—" : String.format(PT_BR, "%,.0f L", value);
    }

    public String horas(Double value) {
        return value == null ? "—" : String.format(PT_BR, "%.1f h", value);
    }

    /** Rótulo em português para status de chamado, de sensor e papéis. */
    public String status(String value) {
        if (value == null) {
            return "—";
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "received" -> "Recebido";
            case "in_progress" -> "Em atendimento";
            case "closed" -> "Encerrado";
            case "normal" -> "Normal";
            case "warning" -> "Alerta";
            case "alarm" -> "Risco máximo";
            case "offline" -> "Sem comunicação";
            case "resident" -> "Morador";
            case "operator" -> "Operadora";
            case "admin" -> "Administrador";
            case "level" -> "Nível";
            case "communication" -> "Comunicação";
            case "sent" -> "Enviado";
            case "failed" -> "Falhou";
            case "skipped" -> "Não enviado";
            default -> value;
        };
    }
}
