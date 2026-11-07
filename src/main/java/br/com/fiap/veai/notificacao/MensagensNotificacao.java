package br.com.fiap.veai.notificacao;

import br.com.fiap.veai.relato.Relato;
import br.com.fiap.veai.relato.StatusRelato;

/** Textos curtos, escritos para serem lidos em voz alta pelo app. */
final class MensagensNotificacao {

    private MensagensNotificacao() {}

    static String novoRelato(Relato r) {
        return "Novo relato de " + r.getTipo().rotulo() + " em " + r.getRegiao().getNome() + ".";
    }

    static String apoio(Relato r) {
        int total = r.getTotalApoios();
        String pessoas = total == 1 ? "1 pessoa já confirmou" : total + " pessoas já confirmaram";
        return "Mais uma pessoa confirmou seu relato de " + r.getTipo().rotulo() + ". Agora " + pessoas + ".";
    }

    /** {@code paraAutor} muda o sujeito: "Seu relato" × "O relato que você apoiou". */
    static String status(Relato r, StatusRelato novo, boolean paraAutor) {
        String sujeito = paraAutor
                ? "Seu relato de " + r.getTipo().rotulo()
                : "O relato de " + r.getTipo().rotulo() + " que você apoiou";
        return switch (novo) {
            case EM_TRIAGEM -> sujeito + " está em análise pela rede de apoio.";
            case ENCAMINHADO -> sujeito + " foi encaminhado ao órgão responsável. Protocolo " + r.getProtocoloOrgao() + ".";
            case RESOLVIDO -> "Boa notícia! " + sujeito + " foi resolvido.";
            case DESCARTADO -> sujeito + " foi encerrado. Motivo: " + r.getMotivoDescarte();
            case ABERTO -> sujeito + " foi reaberto.";
        };
    }
}
