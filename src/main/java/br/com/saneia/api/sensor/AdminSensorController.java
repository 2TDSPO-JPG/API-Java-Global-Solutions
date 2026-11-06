package br.com.saneia.api.sensor;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.saneia.api.common.PageResponses;

@RestController
@RequestMapping("/admin/sensors")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administração", description = "Usuários e catálogo de equipamentos (papel admin)")
public class AdminSensorController {

    private final SensorService sensors;

    public AdminSensorController(SensorService sensors) {
        this.sensors = sensors;
    }

    @GetMapping
    @Operation(summary = "Lista os equipamentos ativos do catálogo", description = "Paginado: page e size.")
    public ResponseEntity<List<SensorResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500));
        return PageResponses.of(sensors.listActive(request), Function.identity());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe de um equipamento ativo")
    public SensorResponse get(@PathVariable Long id) {
        return sensors.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um equipamento (começa offline até a primeira leitura)")
    public SensorResponse create(@Valid @RequestBody SensorInput input) {
        return sensors.create(input);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Atualiza os dados cadastrais (as leituras são preservadas)")
    public SensorResponse update(@PathVariable Long id, @Valid @RequestBody SensorInput input) {
        return sensors.update(id, input);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mesmo que PATCH (atualização completa do cadastro)")
    public SensorResponse replace(@PathVariable Long id, @Valid @RequestBody SensorInput input) {
        return sensors.update(id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retira o equipamento do catálogo, do mapa e da cobertura (exclusão lógica)")
    public void delete(@PathVariable Long id) {
        sensors.retire(id);
    }
}
