package br.com.saneia.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "Informe o nome.") @Size(min = 2, max = 120, message = "O nome deve ter de 2 a 120 caracteres.")
        String name,
        @NotBlank(message = "Informe o e-mail.") @Email(message = "Informe um e-mail válido.")
        @Size(max = 160, message = "O e-mail deve ter até 160 caracteres.")
        String email,
        @NotBlank(message = "Informe a senha.")
        @Size(min = 8, max = 100, message = "A senha deve ter de 8 a 100 caracteres.")
        String password) {
}
