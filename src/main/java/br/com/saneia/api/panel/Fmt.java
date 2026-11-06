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
            case "low" -> "Baixa";
            case "medium" -> "Média";
            case "high" -> "Alta";
            case "critical" -> "Crítica";
            case "resolved" -> "Resolvido";
            case "stabilized" -> "Estabilizado (em observação)";
            case "unfounded" -> "Sem procedência";
            case "forwarded" -> "Encaminhado a outro órgão";
            default -> value;
        };
    }

    /** Rótulo dos tipos de registro do histórico do chamado. */
    public String historico(String type) {
        if (type == null) {
            return "—";
        }
        return switch (type) {
            case "OPENED" -> "Protocolo aberto";
            case "EDITED" -> "Chamado editado";
            case "STATUS" -> "Mudança de status";
            case "PRIORITY" -> "Prioridade alterada";
            case "ASSIGNED" -> "Responsável definido";
            case "SENSOR_LINK" -> "Vínculo com sensor";
            case "REOPENED" -> "Reaberto pela equipe";
            case "AUTO_REOPENED" -> "Reaberto automaticamente";
            case "NOTE" -> "Nota interna";
            case "UPDATE" -> "Complemento do morador";
            case "ASSESSMENT" -> "Avaliação técnica";
            case "DELETED" -> "Excluído pelo morador";
            default -> type;
        };
    }

    /** Tempo restante (ou atraso) até o prazo do chamado. */
    public String prazo(Instant dueAt) {
        if (dueAt == null) {
            return "—";
        }
        Duration left = Duration.between(Instant.now(), dueAt);
        boolean late = left.isNegative();
        Duration abs = left.abs();
        String amount = abs.toHours() >= 48 ? abs.toDays() + " dias" : abs.toHours() >= 1 ? abs.toHours() + " h"
                : Math.max(1, abs.toMinutes()) + " min";
        return late ? "atrasado há " + amount : "vence em " + amount;
    }
}
