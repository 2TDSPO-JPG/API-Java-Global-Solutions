package br.com.saneia.api.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.saneia.api.sensor.Forecast;

@DisplayName("Previsão do tempo até o limite")
class ForecastTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    private static Forecast.Point at(int minutes, double volume) {
        return new Forecast.Point(T0.plus(Duration.ofMinutes(minutes)), volume);
    }

    @Test
    @DisplayName("Subida linear de 100 L/h com 500 L até o limite resulta em 5 h")
    void linearRise() {
        List<Forecast.Point> points = List.of(at(0, 1000), at(60, 1100), at(120, 1200), at(180, 1300));
        assertThat(Forecast.hoursToLimit(points, 1800)).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Menos de 3 leituras: estimativa indisponível")
    void insufficientData() {
        assertThat(Forecast.hoursToLimit(List.of(at(0, 1000), at(60, 1200)), 1800)).isNull();
    }

    @Test
    @DisplayName("Tendência de queda ou limite já atingido: sem estimativa")
    void noRiseOrAlreadyAtLimit() {
        assertThat(Forecast.hoursToLimit(List.of(at(0, 1500), at(30, 1400), at(60, 1300)), 1800)).isNull();
        assertThat(Forecast.hoursToLimit(List.of(at(0, 1700), at(30, 1750), at(60, 1850)), 1800)).isNull();
    }

    @Test
    @DisplayName("Só usa as leituras das últimas 6 horas")
    void usesRecentWindow() {
        List<Forecast.Point> points = List.of(at(0, 100), at(600, 1000), at(660, 1100), at(720, 1200));
        assertThat(Forecast.hoursToLimit(points, 1800)).isEqualTo(6.0);
    }
}
