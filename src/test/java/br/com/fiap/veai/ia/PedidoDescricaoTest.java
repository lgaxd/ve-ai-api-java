package br.com.fiap.veai.ia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.fiap.veai.descricao.ModoDescricao;

class PedidoDescricaoTest {

    @Test
    @DisplayName("modo automático manda ao modelo o que a pessoa acabou de ouvir; manual e TEXTO não")
    void pedidoComContexto() {
        String manual = OpenAiDescritor.pedido(ModoDescricao.AMBIENTE, null);
        assertThat(manual).contains("Não há DESCRIÇÃO ANTERIOR").doesNotContain("CONTINUIDADE");

        var anterior = new ContextoAnterior("Bem à sua frente, a cerca de 3 metros, há um {poste} \"fixo\".", 6);
        String automatico = OpenAiDescritor.pedido(ModoDescricao.AMBIENTE, anterior);
        assertThat(automatico)
                .contains("DESCRIÇÃO ANTERIOR (falada há 6 segundos): \"Bem à sua frente, a cerca de 3 metros, há um poste fixo.\"")
                .contains("regras de CONTINUIDADE");

        assertThat(OpenAiDescritor.pedido(ModoDescricao.TEXTO, anterior)).doesNotContain("DESCRIÇÃO ANTERIOR");
    }

    @Test
    @DisplayName("se o modelo omitir novidade, vale true (na dúvida, o app fala)")
    void novidadePadrao() {
        assertThat(new DescricaoIA(null, null, null, null, null, null, null).normalizada().novidade()).isTrue();
    }
}
