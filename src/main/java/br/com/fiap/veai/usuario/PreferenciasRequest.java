package br.com.fiap.veai.usuario;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PreferenciasRequest(
        @NotNull(message = "Informe a velocidade da voz.")
        @DecimalMin(value = "0.5", message = "A velocidade da voz vai de 0,5 a 2,0.")
        @DecimalMax(value = "2.0", message = "A velocidade da voz vai de 0,5 a 2,0.")
        @Schema(example = "1.25")
        Double velocidadeVoz,

        @NotNull(message = "Informe se a pergunta livre por voz fica ativa.")
        Boolean perguntaLivreAtiva) {}
