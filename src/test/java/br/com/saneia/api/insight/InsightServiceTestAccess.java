package br.com.saneia.api.insight;

/** Expõe o resumo calculado (método de pacote) para o teste unitário. */
public final class InsightServiceTestAccess {

    private InsightServiceTestAccess() {
    }

    public static String fallback(InsightIndicators indicators) {
        return InsightService.fallbackSummary(indicators);
    }
}
