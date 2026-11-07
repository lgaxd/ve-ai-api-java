package br.com.fiap.veai.auth;

import br.com.fiap.veai.usuario.Papel;

/** Quem está chamando a API, extraído do JWT. Injetado como parâmetro nos controllers. */
public record UsuarioLogado(Long id, Papel papel) {

    public boolean ehGestor() {
        return papel == Papel.GESTOR;
    }
}
