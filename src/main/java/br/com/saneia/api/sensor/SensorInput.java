package br.com.saneia.api.sensor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import br.com.saneia.api.common.Coordinate;

/**
 * SensorInput do contrato. Capacidade e limite em litros, raio em metros.
 * {@code address} continua sendo o endereço formatado (até 200 caracteres) e {@code postalAddress} traz os campos
 * separados. Na edição, omitir {@code postalAddress} mantém o endereço estruturado já salvo (cadastros legados).
 */
public record SensorInput(
        @NotBlank(message = "Informe o prefixo.")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,29}$",
                message = "Use de 2 a 30 letras, números, hífens ou sublinhados no prefixo.")
        String prefix,
        @NotBlank(message = "Informe o nome do equipamento.") @Size(max = 100, message = "O nome deve ter até 100 caracteres.")
        String name,
        @NotBlank(message = "Informe o tipo do equipamento.") @Size(max = 100, message = "O tipo deve ter até 100 caracteres.")
        String equipmentType,
        @NotBlank(message = "Informe o endereço.") @Size(max = 200, message = "O endereço deve ter até 200 caracteres.")
        String address,
        @NotBlank(message = "Informe o bairro ou região.") @Size(max = 80, message = "O bairro deve ter até 80 caracteres.")
        String neighborhood,
        @NotNull(message = "Informe as coordenadas.") @Valid
        Coordinate coordinate,
        @NotNull(message = "Informe a capacidade.") @Positive(message = "A capacidade deve ser positiva.")
        Double capacity,
        @NotNull(message = "Informe o limite operacional.") @Positive(message = "O limite deve ser positivo.")
        Double limit,
        @NotNull(message = "Informe o raio de cobertura.") @Positive(message = "Informe um raio de cobertura positivo.")
        Double radiusMeters,
        @Min(value = 1, message = "O intervalo esperado deve ser de 1 a 1440 minutos.")
        @Max(value = 1440, message = "O intervalo esperado deve ser de 1 a 1440 minutos.")
        Integer expectedIntervalMinutes,
        @Valid
        PostalAddress postalAddress) {
}
