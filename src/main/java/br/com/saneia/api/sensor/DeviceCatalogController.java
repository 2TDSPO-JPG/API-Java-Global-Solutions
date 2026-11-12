package br.com.saneia.api.sensor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import br.com.saneia.api.common.ApiException;

@Hidden
@RestController
public class DeviceCatalogController {

    private final SensorService sensors;
    private final byte[] integrationKey;

    public DeviceCatalogController(SensorService sensors,
            @Value("${saneia.integration.key}") String integrationKey) {
        this.sensors = sensors;
        this.integrationKey = integrationKey.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/internal/catalog/sensors/by-prefix/{prefix}")
    public SensorService.DeviceCatalog catalog(@PathVariable String prefix,
            @RequestHeader(name = "X-Saneia-Integration-Key", required = false) String suppliedKey) {
        checkKey(suppliedKey);
        return sensors.deviceCatalog(prefix);
    }

    @GetMapping("/internal/catalog/sensors/demo-sp")
    public List<SensorService.DeviceCatalog> demoCatalog(
            @RequestHeader(name = "X-Saneia-Integration-Key", required = false) String suppliedKey) {
        checkKey(suppliedKey);
        return sensors.demoCatalog();
    }

    private void checkKey(String suppliedKey) {
        if (integrationKey.length < 32 || suppliedKey == null
                || !MessageDigest.isEqual(integrationKey, suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Credencial de integração inválida.");
        }
    }
}
