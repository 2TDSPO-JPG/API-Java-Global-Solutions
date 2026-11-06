package br.com.saneia.api.file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import br.com.saneia.api.common.ApiException;
import br.com.saneia.api.config.SaneiaProperties;

/**
 * Guarda as fotos e gera URLs assinadas (HMAC-SHA256 com validade), acessíveis pelo componente Image do app
 * sem expor o token do usuário. Só quem já pode ver o relato recebe a URL.
 */
@Service
public class FileService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp",
            "image/heic", "image/heif");
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private final StoredFileRepository files;
    private final SaneiaProperties properties;
    private final Clock clock;
    private final byte[] signingKey;

    public FileService(StoredFileRepository files, SaneiaProperties properties, Clock clock) {
        this.files = files;
        this.properties = properties;
        this.clock = clock;
        String secret = properties.files() != null && StringUtils.hasText(properties.files().signingSecret())
                ? properties.files().signingSecret()
                : "files:" + properties.security().jwtSecret();
        this.signingKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Valida e grava a foto enviada em multipart. Retorna null se nenhuma foto foi enviada. */
    @Transactional
    public StoredFile storePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        if (photo.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "A foto é grande demais. Envie uma imagem de até 8 MB.");
        }
        String contentType = photo.getContentType() == null ? "" : photo.getContentType().toLowerCase(Locale.ROOT);
        if (contentType.isEmpty() || contentType.equals("application/octet-stream")) {
            contentType = "image/jpeg"; // alguns clientes móveis não informam o tipo da parte
        }
        if (contentType.equals("image/jpg")) {
            contentType = "image/jpeg";
        }
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw ApiException.badRequest("Envie a foto em JPEG, PNG, WEBP ou HEIC.");
        }
        String name = StringUtils.hasText(photo.getOriginalFilename())
                ? StringUtils.getFilename(photo.getOriginalFilename()) : "relato.jpg";
        if (name.length() > 160) {
            name = name.substring(name.length() - 160);
        }
        try {
            return files.save(new StoredFile(contentType, name, photo.getBytes(), clock.instant()));
        } catch (IOException ex) {
            throw ApiException.badRequest("Não foi possível ler a foto enviada.");
        }
    }

    @Transactional(readOnly = true)
    public StoredFile load(Long id, long expires, String signature) {
        if (clock.instant().getEpochSecond() > expires || !MessageDigest.isEqual(
                sign(id, expires).getBytes(StandardCharsets.UTF_8),
                String.valueOf(signature).getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.forbidden("Link da foto expirado ou inválido. Atualize a tela.");
        }
        StoredFile file = files.findById(id).orElseThrow(() -> ApiException.notFound("Foto não encontrada."));
        file.getContent(); // carrega o conteúdo dentro da transação
        return file;
    }

    /** URL absoluta assinada, válida por saneia.files.url-ttl (padrão 2 h). */
    public String signedUrl(Long fileId) {
        if (fileId == null) {
            return null;
        }
        long expires = Instant.now(clock).plus(properties.files().urlTtl()).getEpochSecond();
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/files/{id}")
                .queryParam("expires", expires)
                .queryParam("signature", sign(fileId, expires))
                .buildAndExpand(fileId)
                .toUriString();
    }

    String sign(Long id, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            byte[] digest = mac.doFinal((id + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC indisponível", ex);
        }
    }
}
