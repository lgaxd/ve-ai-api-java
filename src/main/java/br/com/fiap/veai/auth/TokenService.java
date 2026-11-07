package br.com.fiap.veai.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import br.com.fiap.veai.config.VeAiProperties;
import br.com.fiap.veai.usuario.Usuario;

/** Emite o JWT (HS256): sub = id do usuário, claim {@code papel} = CIDADAO | GESTOR. */
@Service
public class TokenService {

    public static final String CLAIM_PAPEL = "papel";

    private final JwtEncoder encoder;
    private final VeAiProperties.Jwt config;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, VeAiProperties properties, Clock clock) {
        this.encoder = encoder;
        this.config = properties.jwt();
        this.clock = clock;
    }

    public TokenEmitido emitir(Usuario usuario) {
        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(config.validade());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(config.emissor())
                .subject(usuario.getId().toString())
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .claim(CLAIM_PAPEL, usuario.getPapel().name())
                .claim("nome", usuario.getNome())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenEmitido(token, expiraEm);
    }

    public record TokenEmitido(String token, Instant expiraEm) {}
}
