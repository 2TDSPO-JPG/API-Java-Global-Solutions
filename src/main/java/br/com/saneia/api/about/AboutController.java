package br.com.saneia.api.about;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Informações públicas da API: versão, build da pipeline e opção de arquitetura distribuída.
 * Útil para comprovar no vídeo que a alteração feita na branch foi publicada na nuvem.
 */
@RestController
@RequestMapping("/about")
@Tag(name = "Sobre", description = "Versão publicada e arquitetura")
@SecurityRequirements
public class AboutController {

    /** Altere esta mensagem numa branch para demonstrar o fluxo Boards → PR → Build → Release. */
    static final String RELEASE_NOTE = "Saneia API no ar: relatos, monitoramento e insights com IA.";

    private final String version;
    private final String build;

    public AboutController(@Value("${info.app.version:dev}") String version,
            @Value("${info.app.build:local}") String build) {
        this.version = version;
        this.build = build;
    }

    public record About(String name, String version, String build, String releaseNote, String distributedArchitecture,
            List<String> queues) {
    }

    @GetMapping
    @Operation(summary = "Versão, build e arquitetura da API")
    public About about() {
        return new About("Saneia API", version, build, RELEASE_NOTE,
                "Opção A: RabbitMQ com eventos publicados e consumidos de forma assíncrona",
                List.of("saneia.telemetry.readings", "saneia.notifications", "saneia.events.dlq"));
    }
}
