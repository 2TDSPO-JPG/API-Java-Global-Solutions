package br.com.saneia.api.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Chave HS256 do JWT, lida da variável JWT_SECRET. A API não sobe sem um segredo forte. */
@Component
public class JwtKeys {

    private final SecretKey key;

    public JwtKeys(SaneiaProperties properties) {
        String secret = properties.security() == null ? null : properties.security().jwtSecret();
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Defina a variável JWT_SECRET com pelo menos 32 caracteres para assinar os tokens.");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public SecretKey key() {
        return key;
    }
}
