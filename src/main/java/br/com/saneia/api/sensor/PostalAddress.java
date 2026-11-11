package br.com.saneia.api.sensor;

import java.util.Locale;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Endereço estruturado do equipamento (o mesmo formulário de endereço do app).
 * CEP e número são texto: o CEP preserva zeros iniciais e o número aceita "S/N".
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Endereço estruturado do equipamento", example = """
        {"cep":"01001000","street":"Praça da Sé","number":"25","neighborhood":"Sé",
         "city":"São Paulo","state":"SP","complement":null}""")
public record PostalAddress(
        @NotBlank(message = "Informe o CEP.")
        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "Informe o CEP com 8 dígitos.")
        @Schema(example = "01001000", description = "8 dígitos (o hífen é aceito na entrada e removido)")
        String cep,
        @NotBlank(message = "Informe o logradouro.") @Size(max = 120, message = "O logradouro deve ter até 120 caracteres.")
        @Schema(example = "Praça da Sé")
        String street,
        @NotBlank(message = "Informe o número ou S/N.") @Size(max = 10, message = "O número deve ter até 10 caracteres.")
        @Schema(example = "25", description = "Texto: aceita S/N")
        String number,
        @NotBlank(message = "Informe o bairro do endereço.") @Size(max = 80, message = "O bairro deve ter até 80 caracteres.")
        @Schema(example = "Sé")
        String neighborhood,
        @NotBlank(message = "Informe a cidade.") @Size(max = 80, message = "A cidade deve ter até 80 caracteres.")
        @Schema(example = "São Paulo")
        String city,
        @NotBlank(message = "Informe a UF.") @Uf
        @Schema(example = "SP", description = "Sigla de uma das 27 UFs")
        String state,
        @Size(max = 80, message = "O complemento deve ter até 80 caracteres.")
        @Schema(nullable = true, example = "null")
        String complement) {

    public static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT",
            "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    /** Forma persistida: CEP só com dígitos, UF maiúscula, "S/N" padronizado e complemento vazio como nulo. */
    public PostalAddress normalized() {
        String digits = cep.replaceAll("\\D", "");
        String num = number.trim();
        if (num.replaceAll("[\\s/.]", "").equalsIgnoreCase("SN")) {
            num = "S/N";
        }
        String extra = complement == null || complement.isBlank() ? null : complement.trim();
        return new PostalAddress(digits, street.trim(), num, neighborhood.trim(), city.trim(),
                state.trim().toUpperCase(Locale.ROOT), extra);
    }
}
