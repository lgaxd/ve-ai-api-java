package br.com.fiap.veai.usuario;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.hibernate.Hibernate;

import br.com.fiap.veai.regiao.RegiaoResposta;

/** Dados públicos do usuário. {@code regioes} só é preenchido para gestores. */
public record UsuarioResposta(Long id, String nome, String email, Papel papel, double velocidadeVoz,
                              boolean perguntaLivreAtiva, Instant criadoEm, List<RegiaoResposta> regioes) {

    public static UsuarioResposta de(Usuario u) {
        List<RegiaoResposta> regioes = u.getPapel() == Papel.GESTOR && Hibernate.isInitialized(u.getRegioes())
                ? u.getRegioes().stream().map(RegiaoResposta::de).sorted(Comparator.comparing(RegiaoResposta::nome)).toList()
                : List.of();
        return new UsuarioResposta(u.getId(), u.getNome(), u.getEmail(), u.getPapel(), u.getVelocidadeVoz(),
                u.isPerguntaLivreAtiva(), u.getCriadoEm(), regioes);
    }
}
