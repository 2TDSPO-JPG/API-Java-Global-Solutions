package br.com.saneia.api.common;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record Coordinate(
        @NotNull(message = "Informe a latitude.")
        @DecimalMin(value = "-90", message = "Informe latitude entre -90 e 90.")
        @DecimalMax(value = "90", message = "Informe latitude entre -90 e 90.")
        Double latitude,
        @NotNull(message = "Informe a longitude.")
        @DecimalMin(value = "-180", message = "Informe longitude entre -180 e 180.")
        @DecimalMax(value = "180", message = "Informe longitude entre -180 e 180.")
        Double longitude) {
}
