package br.com.fiap.veai.descricao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.MimeTypeUtils;

import br.com.fiap.veai.comum.LimiteExcedidoException;
import br.com.fiap.veai.config.VeAiProperties;
import br.com.fiap.veai.ia.Barreira;
import br.com.fiap.veai.relato.Gravidade;
import br.com.fiap.veai.relato.TipoRelato;

class RegrasDescricaoTest {

    @Test
    @DisplayName("sugere relato só para barreira detectada com confiança mínima")
    void sugestaoDeRelato() {
        var alta = new Barreira(true, TipoRelato.OBSTRUCAO_PERMANENTE, Gravidade.MEDIA, 0.86, "poste");
        assertThat(RegraSugestaoRelato.avaliar(alta, 0.7)).hasValueSatisfying(s -> {
            assertThat(s.tipo()).isEqualTo(TipoRelato.OBSTRUCAO_PERMANENTE);
            assertThat(s.pergunta()).isEqualTo("Parece haver calçada obstruída. Quer avisar a rede de apoio?");
        });
        assertThat(RegraSugestaoRelato.avaliar(new Barreira(true, TipoRelato.RAMPA, Gravidade.ALTA, 0.69, "?"), 0.7)).isEmpty();
        assertThat(RegraSugestaoRelato.avaliar(Barreira.nenhuma(), 0.7)).isEmpty();
        assertThat(RegraSugestaoRelato.avaliar(new Barreira(true, null, null, 0.99, "?"), 0.7)).isEmpty();
        assertThat(RegraSugestaoRelato.avaliar(null, 0.7)).isEmpty();
    }

    @Test
    @DisplayName("formato da foto é conferido pela assinatura dos bytes")
    void assinaturaDaImagem() {
        assertThat(ValidadorImagem.detectar(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo(MimeTypeUtils.IMAGE_JPEG);
        assertThat(ValidadorImagem.detectar(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})).isEqualTo(MimeTypeUtils.IMAGE_PNG);
        assertThat(ValidadorImagem.detectar("RIFF\0\0\0\0WEBPVP8 ".getBytes())).hasToString("image/webp");
        assertThat(ValidadorImagem.detectar("GIF89a".getBytes())).isNull();
        assertThat(ValidadorImagem.detectar(new byte[0])).isNull();
    }

    @Test
    @DisplayName("rate limit: 3 por minuto, a 4ª dá 429 com Retry-After, e a janela seguinte libera")
    void rateLimit() {
        var relogio = new RelogioAjustavel(Instant.parse("2026-11-07T12:00:10Z"));
        var props = new VeAiProperties(null, null, null, new VeAiProperties.RateLimit(3), null, null);
        var limitador = new LimitadorRequisicoes(props, relogio);

        limitador.consumir(1L);
        limitador.consumir(1L);
        limitador.consumir(1L);
        limitador.consumir(2L); // outro usuário tem a própria janela
        assertThatThrownBy(() -> limitador.consumir(1L))
                .isInstanceOfSatisfying(LimiteExcedidoException.class, e -> assertThat(e.segundosParaLiberar()).isEqualTo(50));

        relogio.avancar(Duration.ofSeconds(50));
        limitador.consumir(1L);
    }

    /** Clock que os testes conseguem avançar. */
    static final class RelogioAjustavel extends Clock {
        private Instant agora;

        RelogioAjustavel(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
