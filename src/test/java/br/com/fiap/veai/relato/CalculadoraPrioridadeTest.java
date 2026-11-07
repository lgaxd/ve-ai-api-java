package br.com.fiap.veai.relato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class CalculadoraPrioridadeTest {

    private static final Instant AGORA = Instant.parse("2026-11-07T12:00:00Z");

    @ParameterizedTest(name = "{0} com {1} apoios e {2} dias = {3}")
    @CsvSource({
            "BAIXA, 0, 0, 1",
            "MEDIA, 0, 0, 3",
            "ALTA,  0, 0, 5",
            "ALTA,  2, 60, 75",   // relato #1 do seed: 5 × (1 + 2) + 60
            "MEDIA, 1, 50, 56",
            "BAIXA, 4, 10, 15"})
    @DisplayName("peso da gravidade × (1 + apoios) + dias em aberto")
    void formula(Gravidade gravidade, int apoios, int dias, int esperado) {
        Instant criadoEm = AGORA.minus(Duration.ofDays(dias));
        assertThat(CalculadoraPrioridade.calcular(gravidade, apoios, StatusRelato.ABERTO, criadoEm, AGORA)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @EnumSource(value = StatusRelato.class, names = {"RESOLVIDO", "DESCARTADO"})
    @DisplayName("relato finalizado sai da fila: prioridade 0")
    void finalizadoValeZero(StatusRelato status) {
        assertThat(CalculadoraPrioridade.calcular(Gravidade.ALTA, 10, status, AGORA.minus(Duration.ofDays(90)), AGORA)).isZero();
    }

    @Test
    @DisplayName("dias incompletos não contam e datas no futuro não geram valor negativo")
    void diasArredondadosParaBaixo() {
        assertThat(CalculadoraPrioridade.calcular(Gravidade.MEDIA, 0, StatusRelato.EM_TRIAGEM,
                AGORA.minus(Duration.ofHours(47)), AGORA)).isEqualTo(3 + 1);
        assertThat(CalculadoraPrioridade.calcular(Gravidade.MEDIA, 0, StatusRelato.ABERTO,
                AGORA.plus(Duration.ofHours(1)), AGORA)).isEqualTo(3);
    }
}
