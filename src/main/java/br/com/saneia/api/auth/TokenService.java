package br.com.saneia.api.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import br.com.saneia.api.config.SaneiaProperties;
import br.com.saneia.api.user.AppUser;
import br.com.saneia.api.user.UserResponse;

@Service
public class TokenService {

    static final String ISSUER = "saneia-api";

    private final JwtEncoder encoder;
    private final SaneiaProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, SaneiaProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public SessionResponse issue(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.security().tokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("role", user.getRole().name())
                .claim("name", user.getName())
                .claim("email", user.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new SessionResponse(token, expiresAt, UserResponse.from(user));
    }
}
