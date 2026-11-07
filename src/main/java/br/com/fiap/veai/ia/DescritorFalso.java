package br.com.fiap.veai.ia;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.fiap.veai.descricao.ModoDescricao;
import br.com.fiap.veai.relato.Gravidade;
import br.com.fiap.veai.relato.TipoRelato;

/**
 * Descritor sem IA, para desenvolvimento e testes. A mesma foto sempre gera a mesma resposta
 * (o cenário é escolhido pelo conteúdo dos bytes), e há cenários para cada caminho do app:
 * barreira com confiança alta, barreira com confiança baixa, só perigo momentâneo e foto escura.
 */
@Component
@ConditionalOnProperty(name = "veai.ia.provedor", havingValue = "falso", matchIfMissing = true)
public class DescritorFalso implements DescritorImagem {

    private static final List<DescricaoIA> CENARIOS = List.of(
            new DescricaoIA(
                    List.of(new Perigo("poste no meio da calçada", "frente", "3 m")),
                    "Calçada estreita, com um poste bloqueando a passagem à frente.",
                    "Calçada de concreto com cerca de um metro de largura. A três passos há um poste no meio da faixa "
                            + "livre, deixando pouco espaço para passar. À direita, um muro baixo.",
                    List.of("poste", "muro", "lixeira"),
                    null,
                    new Barreira(true, TipoRelato.OBSTRUCAO_PERMANENTE, Gravidade.MEDIA, 0.86,
                            "Poste fixo ocupando a faixa livre da calçada.")),
            new DescricaoIA(
                    List.of(new Perigo("bicicleta se aproximando", "esquerda", "5 m")),
                    "Calçada larga e plana. Uma bicicleta vem pela esquerda.",
                    "Calçada larga, de piso regular. Há uma bicicleta se aproximando pela esquerda e uma árvore à direita.",
                    List.of("bicicleta", "árvore"),
                    null,
                    Barreira.nenhuma()),
            new DescricaoIA(
                    List.of(new Perigo("buraco na calçada", "frente", "2 m")),
                    "Buraco na calçada, logo à frente.",
                    "Calçada de concreto com um buraco de uns 40 centímetros a dois passos. "
                            + "Dá para desviar pela direita, junto ao meio-fio.",
                    List.of("buraco", "meio-fio"),
                    null,
                    new Barreira(true, TipoRelato.CALCADA_DANIFICADA, Gravidade.ALTA, 0.93,
                            "Buraco profundo na faixa de circulação.")),
            new DescricaoIA(
                    List.of(),
                    "Não consegui ver bem: a foto está escura.",
                    "A foto está escura demais para descrever o ambiente com segurança. "
                            + "Tente de novo com mais luz. Não posso confirmar se o caminho está livre.",
                    List.of(),
                    null,
                    new Barreira(true, TipoRelato.OUTRO, Gravidade.BAIXA, 0.35, "Imagem escura: baixa confiança.")));

    private static final DescricaoIA TEXTO = new DescricaoIA(
            List.of(),
            "Placa: Farmácia aberta 24 horas.",
            "Farmácia São Paulo. Aberta 24 horas. Entrada pela lateral.",
            List.of("placa"),
            "Farmácia São Paulo. Aberta 24 horas. Entrada pela lateral.",
            Barreira.nenhuma());

    @Override
    public DescricaoIA descrever(ImagemRecebida imagem, ModoDescricao modo) {
        if (modo == ModoDescricao.TEXTO) {
            return TEXTO;
        }
        return CENARIOS.get(Math.floorMod(Arrays.hashCode(imagem.bytes()), CENARIOS.size()));
    }

    @Override
    public String responder(ImagemRecebida imagem, String pergunta) {
        return "Pela foto, não há degraus à frente. O poste fica a cerca de três passos, à direita.";
    }

    @Override
    public String provedor() {
        return "falso";
    }

    /** Índice do cenário que uma imagem gera (usado nos testes para montar fotos de cada caso). */
    public static int cenarioDe(byte[] bytes) {
        return Math.floorMod(Arrays.hashCode(bytes), CENARIOS.size());
    }
}
