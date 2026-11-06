package br.com.saneia.api.report;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import br.com.saneia.api.common.Coordinate;

/** ReportInput do contrato (JSON puro ou parte textual "report" no multipart). */
public record ReportInput(
        @NotBlank(message = "Selecione a categoria do problema.") @Size(max = 40)
        String category,
        @NotBlank(message = "Descreva o problema.")
        @Size(max = 1000, message = "A descrição deve ter até 1000 caracteres.")
        String description,
        @NotBlank(message = "Informe o endereço ou uma referência.")
        @Size(max = 200, message = "O endereço deve ter até 200 caracteres.")
        String address,
        @NotBlank(message = "Informe o bairro.") @Size(max = 80, message = "O bairro deve ter até 80 caracteres.")
        String neighborhood,
        @NotNull(message = "Confirme a localização no mapa.") @Valid
        Coordinate coordinate) {
}
