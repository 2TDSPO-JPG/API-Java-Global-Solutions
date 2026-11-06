package br.com.saneia.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";
    public static final String DEVICE_KEY = "deviceKey";

    @Bean
    public OpenAPI saneiaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Saneia API")
                        .version("1.0.0")
                        .description("""
                                Monitoramento e atendimento para o saneamento local (Global Solution FIAP 2026/2 · CitizenTech).
                                Faça login em POST /auth/login, copie o token e clique em Authorize.
                                Listas aceitam page e size; o total vem nos cabeçalhos X-Total-Count e X-Total-Pages.""")
                        .contact(new Contact().name("Equipe Saneia")))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes(DEVICE_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Device-Key")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
