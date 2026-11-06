package br.com.saneia.api.sensor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/coverage")
@Tag(name = "Relatos", description = "Relatos dos moradores e atendimento da operadora")
public class CoverageController {

    private final SensorService sensors;

    public CoverageController(SensorService sensors) {
        this.sensors = sensors;
    }

    @GetMapping
    @Operation(summary = "Indica se o ponto tem equipamento ativo dentro do raio de cobertura")
    public CoverageResponse coverage(@RequestParam double latitude, @RequestParam double longitude) {
        return sensors.coverage(latitude, longitude);
    }
}
