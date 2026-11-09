package br.com.fiap.veai.ia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.fiap.veai.descricao.ModoDescricao;

class PedidoDescricaoTest {

    @Test
    @DisplayName("modo automático manda ao modelo o que a pessoa ouviu, a cena e os perigos da foto anterior")
    void pedidoComContexto() {
        String manual = OpenAiDescritor.pedido(ModoDescricao.AMBIENTE, null);
        assertThat(manual).contains("Não há FOTO ANTERIOR").doesNotContain("CONTINUIDADE");

        var anterior = new ContextoAnterior("O {poste} \"continua\" no mesmo lugar, à sua frente.",
                "Poste no meio da calçada, a cerca de 3 metros.",
                List.of(new Perigo("Poste no meio da calçada.", "frente", "cerca de 3 metros")), 6);
        assertThat(OpenAiDescritor.pedido(ModoDescricao.AMBIENTE, anterior))
                .contains("FOTO ANTERIOR (há 6 segundos)")
                .contains("- O que a pessoa ouviu: \"O poste continua no mesmo lugar, à sua frente.\"")
                .contains("- Cena: \"Poste no meio da calçada, a cerca de 3 metros.\"")
                .contains("- Perigos: Poste no meio da calçada. (frente, cerca de 3 metros)")
                .contains("regras de CONTINUIDADE");

        var semPerigo = new ContextoAnterior("Nada mudou à sua frente.", "Calçada larga.", null, 4);
        assertThat(OpenAiDescritor.pedido(ModoDescricao.AMBIENTE, semPerigo)).contains("- Perigos: nenhum");
        assertThat(OpenAiDescritor.pedido(ModoDescricao.TEXTO, anterior)).doesNotContain("FOTO ANTERIOR");
    }

    @Test
    @DisplayName("se o modelo omitir novidade, vale true; a confirmação do descritor falso é uma frase curta")
    void novidadePadraoEConfirmacao() {
        assertThat(new DescricaoIA(null, null, null, null, null, null, null).normalizada().novidade()).isTrue();
        assertThat(DescritorFalso.confirmacao(List.of("poste", "muro", "lixeira")))
                .isEqualTo("Poste, muro e lixeira continuam no mesmo lugar, à sua frente.");
        assertThat(DescritorFalso.confirmacao(List.of("escada"))).isEqualTo("Escada continua no mesmo lugar, à sua frente.");
        assertThat(DescritorFalso.confirmacao(List.of())).isEqualTo("Nada mudou à sua frente.");
    }
}
