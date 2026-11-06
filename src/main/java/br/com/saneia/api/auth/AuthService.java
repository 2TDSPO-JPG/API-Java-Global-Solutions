package br.com.saneia.api.auth;

import java.time.Clock;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.saneia.api.common.ApiException;
import br.com.saneia.api.user.AppUser;
import br.com.saneia.api.user.AppUserRepository;
import br.com.saneia.api.user.Role;

@Service
public class AuthService {


    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final Clock clock;
    /** Hash usado quando o e-mail não existe: o tempo de resposta fica igual (evita enumeração de contas). */
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokens, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("saneia-conta-inexistente");
    }

    @Transactional(readOnly = true)
    public SessionResponse login(LoginRequest request) {
        AppUser user = users.findByEmailIgnoreCase(normalize(request.email())).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        if (!passwordEncoder.matches(request.password(), hash) || user == null) {
            throw new BadCredentialsException("E-mail ou senha incorretos.");
        }
        return tokens.issue(user);
    }

    /** Cadastro público: cria sempre um morador. Operadora e administração são provisionadas no servidor. */
    @Transactional
    public SessionResponse signup(SignupRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Já existe uma conta com este e-mail.");
        }
        try {
            AppUser user = users.saveAndFlush(new AppUser(request.name().trim(), email,
                    passwordEncoder.encode(request.password()), Role.RESIDENT, clock.instant()));
            return tokens.issue(user);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("Já existe uma conta com este e-mail.");
        }
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
