package br.com.saneia.api.file;

import java.time.Duration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/files")
@Tag(name = "Fotos", description = "Download das fotos por URL assinada")
@SecurityRequirements
public class FileController {

    private final FileService files;

    public FileController(FileService files) {
        this.files = files;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Entrega a foto se a assinatura e a validade da URL forem válidas")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @RequestParam long expires,
            @RequestParam String signature) {
        StoredFile file = files.load(id, expires, signature);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePrivate())
                .body(file.getContent());
    }
}
