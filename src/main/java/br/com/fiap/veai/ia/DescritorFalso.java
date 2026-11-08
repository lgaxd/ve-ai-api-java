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
                    List.of(new Perigo("Poste no meio da calçada, deixando pouco espaço para passar.", "frente", "cerca de 3 metros")),
                    "Poste no meio da calçada, a cerca de 3 metros. Calçada estreita.",
                    "Bem à sua frente, a cerca de 3 metros, há um poste no meio da calçada, deixando pouco espaço para passar. "
                            + "Calçada estreita, de concreto. Ao seu lado direito, um muro baixo acompanha a calçada.",
                    List.of("poste", "muro", "lixeira"),
                    null,
                    new Barreira(true, TipoRelato.OBSTRUCAO_PERMANENTE, Gravidade.MEDIA, 0.86,
                            "Poste fixo ocupando a faixa livre da calçada.")),
            new DescricaoIA(
                    List.of(new Perigo("Bicicleta vindo em sua direção pela calçada.", "frente-esquerda", "cerca de 5 metros")),
                    "Calçada larga e plana.",
                    "À frente, levemente à esquerda, a cerca de 5 metros, uma bicicleta vem em sua direção. "
                            + "Calçada larga, de piso regular. À frente, mais para a direita, há uma árvore.",
                    List.of("bicicleta", "árvore"),
                    null,
                    Barreira.nenhuma()),
            new DescricaoIA(
                    List.of(new Perigo("Buraco largo no piso da calçada.", "frente", "cerca de 2 metros")),
                    "Buraco na calçada, a cerca de 2 metros. Dá para desviar pela direita.",
                    "Bem à sua frente, a cerca de 2 metros, há um buraco largo no piso. "
                            + "Dá para desviar pela direita, junto ao meio-fio. Calçada de concreto.",
                    List.of("buraco", "meio-fio"),
                    null,
                    new Barreira(true, TipoRelato.CALCADA_DANIFICADA, Gravidade.ALTA, 0.93,
                            "Buraco profundo na faixa de circulação.")),
            new DescricaoIA(
                    List.of(),
                    "A foto está escura. Aponte o celular para a frente, com mais luz.",
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
        return "Não vejo degraus à sua frente. O poste fica à frente, levemente à direita, a cerca de 3 metros.";
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
