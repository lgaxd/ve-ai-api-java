package br.com.fiap.veai.relato;

import static br.com.fiap.veai.relato.StatusRelato.ABERTO;
import static br.com.fiap.veai.relato.StatusRelato.DESCARTADO;
import static br.com.fiap.veai.relato.StatusRelato.EM_TRIAGEM;
import static br.com.fiap.veai.relato.StatusRelato.ENCAMINHADO;
import static br.com.fiap.veai.relato.StatusRelato.RESOLVIDO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.com.fiap.veai.comum.ApiException;
import br.com.fiap.veai.regiao.Regiao;
import br.com.fiap.veai.usuario.Usuario;

class StatusRelatoTest {

    private static final Instant AGORA = Instant.parse("2026-11-07T12:00:00Z");

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "ABERTO, EM_TRIAGEM", "EM_TRIAGEM, ENCAMINHADO", "ENCAMINHADO, RESOLVIDO",
            "ABERTO, DESCARTADO", "EM_TRIAGEM, DESCARTADO", "ENCAMINHADO, DESCARTADO"})
    @DisplayName("transições válidas")
    void validas(StatusRelato de, StatusRelato para) {
        assertThat(de.podeIrPara(para)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "ABERTO, ENCAMINHADO", "ABERTO, RESOLVIDO", "EM_TRIAGEM, RESOLVIDO",   // pular etapa
            "EM_TRIAGEM, ABERTO", "ENCAMINHADO, EM_TRIAGEM",                       // voltar
            "RESOLVIDO, ABERTO", "RESOLVIDO, DESCARTADO", "DESCARTADO, ABERTO",    // sair de status final
            "ABERTO, ABERTO"})
    @DisplayName("transições inválidas")
    void invalidas(StatusRelato de, StatusRelato para) {
        assertThat(de.podeIrPara(para)).isFalse();
    }

    @Test
    @DisplayName("ENCAMINHADO exige protocolo e DESCARTADO exige motivo")
    void exigencias() {
        Relato r = novoRelato();
        r.alterarStatus(EM_TRIAGEM, null, null, AGORA);
        assertThatThrownBy(() -> r.alterarStatus(ENCAMINHADO, "  ", null, AGORA))
                .isInstanceOf(ApiException.class).hasMessageContaining("protocolo");
        assertThatThrownBy(() -> r.alterarStatus(DESCARTADO, null, null, AGORA))
                .isInstanceOf(ApiException.class).hasMessageContaining("motivo");

        assertThat(r.alterarStatus(ENCAMINHADO, " SP156-1 ", null, AGORA)).isEqualTo(EM_TRIAGEM);
        assertThat(r.getProtocoloOrgao()).isEqualTo("SP156-1");
    }

    @Test
    @DisplayName("finalizar zera a prioridade; editar só enquanto ABERTO")
    void finalizarEEditar() {
        Relato r = novoRelato();
        assertThat(r.getPrioridade()).isEqualTo(5);
        r.registrarApoio(AGORA);
        assertThat(r.getPrioridade()).isEqualTo(10);

        r.alterarStatus(DESCARTADO, null, "Não encontrado na vistoria", AGORA);
        assertThat(r.getPrioridade()).isZero();
        assertThatThrownBy(() -> r.editar(TipoRelato.RAMPA, Gravidade.BAIXA, "x", null, AGORA))
                .isInstanceOf(ApiException.class).hasMessageContaining("aberto");
        assertThat(r.getStatus()).isEqualTo(DESCARTADO);
        assertThat(ABERTO.ehFinal()).isFalse();
        assertThat(RESOLVIDO.ehFinal()).isTrue();
    }

    private static Relato novoRelato() {
        Usuario cidadao = Usuario.novoCidadao("Teste", "teste@veai.dev", "hash", AGORA);
        Regiao regiao = new Regiao("Sé", "São Paulo", "SP", -23.55, -46.63);
        return Relato.novo(cidadao, null, regiao, TipoRelato.CALCADA_DANIFICADA, Gravidade.ALTA, "Buraco", -23.55, -46.63,
                null, AGORA);
    }
}
