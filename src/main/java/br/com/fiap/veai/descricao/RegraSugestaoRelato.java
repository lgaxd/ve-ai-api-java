package br.com.fiap.veai.descricao;

import java.util.Optional;

import br.com.fiap.veai.descricao.DescricaoDtos.SugestaoRelato;
import br.com.fiap.veai.ia.Barreira;
import br.com.fiap.veai.relato.Gravidade;
import br.com.fiap.veai.relato.TipoRelato;

/**
 * Decide se o app deve perguntar "Quer avisar a rede de apoio?": só para barreira estrutural
 * com confiança acima do mínimo. Perigos momentâneos nunca chegam aqui (não são barreira).
 */
public final class RegraSugestaoRelato {

    private RegraSugestaoRelato() {}

    public static Optional<SugestaoRelato> avaliar(Barreira barreira, double confiancaMinima) {
        if (barreira == null || !barreira.detectada() || barreira.tipo() == null
                || barreira.confianca() < confiancaMinima) {
            return Optional.empty();
        }
        Gravidade gravidade = barreira.gravidade() == null ? Gravidade.MEDIA : barreira.gravidade();
        return Optional.of(new SugestaoRelato(barreira.tipo(), gravidade, pergunta(barreira.tipo())));
    }

    static String pergunta(TipoRelato tipo) {
        String oQue = tipo == TipoRelato.OUTRO ? "uma barreira no caminho" : tipo.rotulo();
        return "Parece haver " + oQue + ". Quer avisar a rede de apoio?";
    }
}
