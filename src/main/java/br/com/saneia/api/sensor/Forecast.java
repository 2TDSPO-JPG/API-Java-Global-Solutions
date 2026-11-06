package br.com.saneia.api.sensor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Estimativa do tempo até o limite por regressão linear (litros/hora) nas leituras recentes.
 * Sem dados suficientes, tendência de queda ou dados desatualizados, a estimativa fica indisponível (null).
 */
public final class Forecast {

    public static final int MIN_POINTS = 3;
    public static final Duration WINDOW = Duration.ofHours(6);

    private Forecast() {
    }

    public record Point(Instant at, double volume) {
    }

    /** Ritmo em litros por hora, ou null se não houver pelo menos {@link #MIN_POINTS} leituras em horários distintos. */
    public static Double slopeLitersPerHour(List<Point> points) {
        if (points == null || points.size() < MIN_POINTS) {
            return null;
        }
        Instant origin = points.get(0).at();
        double n = points.size();
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;
        for (Point point : points) {
            double x = Duration.between(origin, point.at()).toMillis() / 3_600_000d;
            double y = point.volume();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denominator = n * sumXX - sumX * sumX;
        if (Math.abs(denominator) < 1e-9) {
            return null;
        }
        return (n * sumXY - sumX * sumY) / denominator;
    }

    /**
     * Horas até atingir o limite a partir da última leitura, arredondado a 0,1 h.
     * Retorna null se o volume já atingiu o limite, se o ritmo não é de subida ou se faltam dados.
     */
    public static Double hoursToLimit(List<Point> chronological, double limit) {
        if (chronological == null || chronological.isEmpty()) {
            return null;
        }
        Point last = chronological.get(chronological.size() - 1);
        if (last.volume() >= limit) {
            return null;
        }
        List<Point> recent = chronological.stream()
                .filter(point -> !point.at().isBefore(last.at().minus(WINDOW)))
                .toList();
        Double slope = slopeLitersPerHour(recent);
        if (slope == null || slope <= 0.0001) {
            return null;
        }
        double hours = (limit - last.volume()) / slope;
        return Math.round(hours * 10) / 10.0;
    }
}
