package br.com.saneia.api.insight;

/**
 * { summary } do contrato. aiGenerated indica se o texto veio do LLM (Spring AI) ou do resumo calculado
 * de reserva (sem chave configurada ou indisponibilidade do provedor).
 */
public record InsightResponse(String summary, boolean aiGenerated, String model, InsightIndicators indicators) {
}
