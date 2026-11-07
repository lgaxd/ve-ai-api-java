package br.com.fiap.veai.comum;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;

/** Formato único de erro da API, igual em Java e .NET. */
public record ErroResposta(Instant timestamp, int status, String erro, String mensagem, List<CampoErro> campos) {

    public static ErroResposta de(HttpStatus status, String mensagem) {
        return de(status, mensagem, List.of());
    }

    public static ErroResposta de(HttpStatus status, String mensagem, List<CampoErro> campos) {
        return new ErroResposta(Instant.now(), status.value(), status.getReasonPhrase(), mensagem, campos);
    }
}
