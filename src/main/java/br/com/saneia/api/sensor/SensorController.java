package br.com.saneia.api.sensor;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.saneia.api.common.PageResponses;
import br.com.saneia.api.insight.InsightRequest;
import br.com.saneia.api.insight.InsightResponse;
import br.com.saneia.api.insight.InsightService;

@RestController
@RequestMapping("/sensors")
@PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
@Tag(name = "Monitoramento", description = "Mapa, ficha do equipamento e insights com IA (operadora)")
public class SensorController {

    private final SensorService sensors;
    private final InsightService insights;

    public SensorController(SensorService sensors, InsightService insights) {
        this.sensors = sensors;
        this.insights = insights;
    }

    @GetMapping
    @Operation(summary = "Equipamentos ativos com estado, volume e últimas leituras", description = "Paginado: page e size.")
    public ResponseEntity<List<SensorResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size,
            @RequestParam(defaultValue = "false") boolean catalogOnly) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500));
        return PageResponses.of(catalogOnly ? sensors.listCatalog(request) : sensors.listActive(request), Function.identity());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ficha do equipamento com amostras, eventos e previsão")
    public SensorResponse detail(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean catalogOnly) {
        return catalogOnly ? sensors.catalogDetail(id) : sensors.detail(id);
    }

    @PostMapping("/{id}/insights")
    @Operation(summary = "Resumo do período gerado com Spring AI (Gemini)",
            description = "O backend calcula os indicadores no banco; o LLM apenas explica os números e suas limitações.")
    public InsightResponse insights(@PathVariable Long id, @Valid @RequestBody InsightRequest request) {
        return insights.summarize(id, request);
    }
}
