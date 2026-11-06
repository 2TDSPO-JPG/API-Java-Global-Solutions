package br.com.saneia.api.report;

import java.util.List;

/** Categorias enviadas pelo app. */
public final class ReportCategory {

    public static final List<String> ALL = List.of("Retorno de esgoto", "Vazamento", "Mau cheiro", "Outro problema");

    private ReportCategory() {
    }

    public static boolean isValid(String category) {
        return category != null && ALL.contains(category.trim());
    }
}
