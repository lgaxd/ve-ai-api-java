package br.com.fiap.veai.notificacao;

import java.time.Instant;

public record NotificacaoResposta(Long id, Long relatoId, String mensagem, boolean lida, Instant criadoEm) {

    public static NotificacaoResposta de(Notificacao n) {
        return new NotificacaoResposta(n.getId(), n.getRelato() == null ? null : n.getRelato().getId(), n.getMensagem(),
                n.isLida(), n.getCriadoEm());
    }
}
