package br.com.saneia.api.insight;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * IA generativa no atendimento (Spring AI): sugere a mensagem ao morador e escreve o boletim operacional do dia.
 * Recebe apenas dados já calculados pelo backend; sem chave ou com falha do provedor, usa um texto de reserva.
 */
@Service
public class OperationsAiService {

    private static final Logger log = LoggerFactory.getLogger(OperationsAiService.class);

    static final String REPLY_PROMPT = """
            Você redige mensagens da operadora de saneamento para moradores, em português do Brasil.
            Escreva de 2 a 3 frases cordiais e claras, citando o protocolo. Use apenas os dados recebidos:
            não invente datas, prazos, causas nem serviços que não constem. Não use markdown nem emojis.
            Se o tipo for "andamento", diga o que está sendo feito agora. Se for "desfecho", explique o resultado
            do atendimento e oriente o morador a enviar um complemento pelo app se o problema voltar.
            """;

    static final String BULLETIN_PROMPT = """
            Você escreve o boletim operacional do Saneia para a gestão de saneamento, em português do Brasil.
            Em até 5 frases curtas, resuma a situação usando somente os números e nomes recebidos: áreas em risco,
            chamados abertos e fora do prazo, equipamentos em alarme ou sem comunicação. Termine com a prioridade
            sugerida para as próximas horas. Não invente dados e não use markdown, listas nem emojis.
            """;

    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final String apiKey;

    public OperationsAiService(ObjectProvider<ChatClient.Builder> chatClientBuilder,
            @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.chatClientBuilder = chatClientBuilder;
        this.apiKey = apiKey;
    }

    public record AiText(String text, boolean aiGenerated) {
    }

    public record ReplyContext(String protocol, String category, String neighborhood, String description,
            String status, String sensorStatus, String progressNote, String resolution, String kind) {
    }

    public record BulletinContext(long openReports, long overdue, long recurrent, String averageHandling,
            List<String> criticalAreas, List<String> highAreas, List<String> sensorsInAlarm,
            List<String> sensorsOffline) {
    }

    public AiText suggestReply(ReplyContext context) {
        String data = "tipo=" + context.kind() + "; protocolo=" + context.protocol() + "; categoria=" + context.category()
                + "; area=" + context.neighborhood() + "; relato=" + context.description()
                + "; status=" + context.status() + "; sensor=" + nullToDash(context.sensorStatus())
                + "; nota_atual=" + nullToDash(context.progressNote()) + "; desfecho_atual=" + nullToDash(context.resolution());
        return complete(REPLY_PROMPT, data).map(text -> new AiText(text, true))
                .orElseGet(() -> new AiText(fallbackReply(context), false));
    }

    public AiText bulletin(BulletinContext context) {
        String data = "chamados_abertos=" + context.openReports() + "; fora_do_prazo=" + context.overdue()
                + "; recorrentes=" + context.recurrent() + "; tempo_medio=" + context.averageHandling()
                + "; areas_risco_maximo=" + context.criticalAreas() + "; areas_risco_alto=" + context.highAreas()
                + "; equipamentos_em_alarme=" + context.sensorsInAlarm()
                + "; equipamentos_sem_comunicacao=" + context.sensorsOffline();
        return complete(BULLETIN_PROMPT, data).map(text -> new AiText(text, true))
                .orElseGet(() -> new AiText(fallbackBulletin(context), false));
    }

    boolean enabled() {
        return StringUtils.hasText(apiKey) && !"desativado".equalsIgnoreCase(apiKey.trim())
                && chatClientBuilder.getIfAvailable() != null;
    }

    private java.util.Optional<String> complete(String system, String user) {
        if (!enabled()) {
            return java.util.Optional.empty();
        }
        try {
            String text = chatClientBuilder.getObject().build().prompt().system(system).user(user).call().content();
            return StringUtils.hasText(text) ? java.util.Optional.of(text.trim()) : java.util.Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("LLM indisponível: {}", ex.getMessage());
            return java.util.Optional.empty();
        }
    }

    static String fallbackReply(ReplyContext c) {
        if ("desfecho".equals(c.kind())) {
            return "O atendimento do protocolo " + c.protocol() + " (" + c.category().toLowerCase(Locale.ROOT)
                    + " em " + c.neighborhood() + ") foi concluído pela equipe. Obrigado por avisar. "
                    + "Se o problema voltar, envie um complemento pelo app que retomamos o acompanhamento.";
        }
        return "Recebemos o protocolo " + c.protocol() + " sobre " + c.category().toLowerCase(Locale.ROOT) + " em "
                + c.neighborhood() + " e a equipe já está acompanhando o caso. "
                + "Você será avisado pelo app a cada atualização do atendimento.";
    }

    static String fallbackBulletin(BulletinContext c) {
        StringBuilder text = new StringBuilder();
        text.append("Há ").append(c.openReports()).append(" chamado(s) aberto(s)");
        text.append(c.overdue() > 0 ? ", sendo " + c.overdue() + " fora do prazo" : ", todos dentro do prazo");
        text.append(c.recurrent() > 0 ? " e " + c.recurrent() + " recorrente(s). " : ". ");
        if (!c.criticalAreas().isEmpty()) {
            text.append("Risco máximo em ").append(String.join(", ", c.criticalAreas()))
                    .append(" (equipamentos em alarme: ").append(String.join(", ", c.sensorsInAlarm())).append("). ");
        } else {
            text.append("Nenhuma área em risco máximo. ");
        }
        if (!c.highAreas().isEmpty()) {
            text.append("Atenção alta em ").append(String.join(", ", c.highAreas())).append(". ");
        }
        if (!c.sensorsOffline().isEmpty()) {
            text.append("Sem comunicação: ").append(String.join(", ", c.sensorsOffline())).append(". ");
        }
        text.append("Tempo médio de atendimento: ").append(c.averageHandling()).append(". ");
        text.append(!c.criticalAreas().isEmpty() ? "Prioridade: deslocar equipe às áreas em risco máximo."
                : c.overdue() > 0 ? "Prioridade: zerar os chamados fora do prazo."
                : "Prioridade: manter o acompanhamento de rotina.");
        return text.toString();
    }

    private static String nullToDash(String value) {
        return value == null ? "-" : value;
    }
}
