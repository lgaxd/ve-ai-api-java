package br.com.fiap.veai.auth;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.veai.auth.AuthDtos.LoginRequest;
import br.com.fiap.veai.auth.AuthDtos.RegistroRequest;
import br.com.fiap.veai.auth.AuthDtos.TokenResposta;
import br.com.fiap.veai.comum.ApiException;
import br.com.fiap.veai.usuario.Usuario;
import br.com.fiap.veai.usuario.UsuarioRepository;
import br.com.fiap.veai.usuario.UsuarioResposta;

@Service
public class AuthService {

    /** Hash válido usado quando o e-mail não existe: o tempo de resposta não revela se a conta existe. */
    private static final String HASH_FICTICIO = "$2a$10$xCHtq1/BEvjc2Bwk9iQ37O84OOuuVQPqIF/pYmbI0hxqFP3jXuOBi";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final Clock clock;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, TokenService tokenService, Clock clock) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    /** O cadastro aberto cria apenas CIDADAO. Gestores vêm do seed. */
    @Transactional
    public UsuarioResposta registrar(RegistroRequest req) {
        String email = req.email().strip().toLowerCase();
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflito("Já existe uma conta com este e-mail.");
        }
        Usuario novo = Usuario.novoCidadao(req.nome().strip(), email, passwordEncoder.encode(req.senha()), clock.instant());
        return UsuarioResposta.de(usuarios.save(novo));
    }

    @Transactional(readOnly = true)
    public TokenResposta login(LoginRequest req) {
        var usuario = usuarios.findByEmailIgnoreCase(req.email().strip());
        String hash = usuario.map(Usuario::getSenhaHash).orElse(HASH_FICTICIO);
        boolean senhaConfere = passwordEncoder.matches(req.senha(), hash);
        if (usuario.isEmpty() || !senhaConfere) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
        }
        Usuario u = usuarios.buscarComRegioes(usuario.get().getId()).orElseThrow();
        var token = tokenService.emitir(u);
        return new TokenResposta(token.token(), "Bearer", token.expiraEm(), u.getPapel(), UsuarioResposta.de(u));
    }
}
