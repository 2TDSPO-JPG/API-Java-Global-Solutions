package br.com.saneia.api.config;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import br.com.saneia.api.common.ErrorResponse;
import br.com.saneia.api.common.PageResponses;

/**
 * Spring Security stateless com JWT (HS256).
 * <ul>
 *   <li>/painel/** tem cadeia própria (formulário + sessão) para o painel administrativo em Thymeleaf.</li>
 *   <li>Rotas públicas: login, cadastro, Swagger, health e fotos com URL assinada.</li>
 *   <li>/admin/** exige ADMIN; /reports, /sensors e /coverage exigem conta autenticada e as regras de papel
 *   e propriedade são aplicadas nos serviços e com @PreAuthorize.</li>
 *   <li>/telemetry/** é autenticado pelo cabeçalho X-Device-Key (dispositivo IoT), validado no controller.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Painel administrativo (Thymeleaf): login por formulário com sessão e CSRF, apenas para o papel ADMIN.
     * Avaliado antes da cadeia da API por causa do @Order(1).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain panelSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/painel/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/painel/login").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage("/painel/login")
                        .loginProcessingUrl("/painel/login")
                        .usernameParameter("email")
                        .passwordParameter("senha")
                        .defaultSuccessUrl("/painel", true)
                        .failureUrl("/painel/login?erro")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/painel/sair")
                        .logoutSuccessUrl("/painel/login?saiu"))
                .exceptionHandling(ex -> ex.accessDeniedPage("/painel/login?negado"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        return http.build();
    }

    /** API REST consumida pelo app: stateless, JWT Bearer. */
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/signup").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/error", "/about").permitAll()
                        .requestMatchers(HttpMethod.GET, "/files/**").permitAll()
                        .requestMatchers("/telemetry/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(request, response, HttpStatus.UNAUTHORIZED,
                                        "Sessão inválida ou expirada. Entre novamente.", "UNAUTHORIZED"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(request, response, HttpStatus.FORBIDDEN,
                                        "Sua conta não tem permissão para esta ação.", "FORBIDDEN")))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(request, response, HttpStatus.UNAUTHORIZED,
                                        "Sessão inválida ou expirada. Entre novamente.", "UNAUTHORIZED"))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(request, response, HttpStatus.FORBIDDEN,
                                        "Sua conta não tem permissão para esta ação.", "FORBIDDEN")));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtKeys keys) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(keys.key()));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtKeys keys) {
        return NimbusJwtDecoder.withSecretKey(keys.key()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SaneiaProperties properties) {
        List<String> origins = properties.security() == null || properties.security().corsOrigins() == null
                ? List.of("*") : properties.security().corsOrigins();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Device-Key"));
        config.setExposedHeaders(List.of(PageResponses.TOTAL_COUNT, PageResponses.TOTAL_PAGES,
                PageResponses.PAGE, PageResponses.PAGE_SIZE));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            String message, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = new ErrorResponse(message, code, status.value(), request.getRequestURI(), Instant.now(),
                null, null, null);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
