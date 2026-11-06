package br.com.saneia.api.telemetry;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.saneia.api.config.OpenApiConfig;

@RestController
@RequestMapping("/telemetry")
@Tag(name = "Telemetria IoT", description = "Entrada das leituras do Wokwi (autenticada por X-Device-Key)")
public class TelemetryController {

    private final TelemetryService telemetry;

    public TelemetryController(TelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    @PostMapping("/readings")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SecurityRequirement(name = OpenApiConfig.DEVICE_KEY)
    @Operation(summary = "Recebe uma leitura e publica na fila saneia.telemetry.readings (processamento assíncrono)")
    public ReadingAccepted receive(@RequestHeader(name = "X-Device-Key", required = false) String deviceKey,
            @Valid @RequestBody ReadingRequest request) {
        telemetry.checkDeviceKey(deviceKey);
        return telemetry.accept(request);
    }
}
