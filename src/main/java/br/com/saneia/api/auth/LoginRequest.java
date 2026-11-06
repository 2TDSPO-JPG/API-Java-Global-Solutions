package br.com.saneia.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Informe o e-mail.") @Email(message = "Informe um e-mail válido.") @Size(max = 160) String email,
        @NotBlank(message = "Informe a senha.") @Size(max = 100) String password) {
}
