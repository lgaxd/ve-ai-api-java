package br.com.fiap.veai.comum;

import org.springframework.http.HttpStatus;

/** Erro de negócio com status HTTP e mensagem pronta para ser falada pelo app. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static ApiException naoEncontrado(String mensagem) {
        return new ApiException(HttpStatus.NOT_FOUND, mensagem);
    }

    public static ApiException conflito(String mensagem) {
        return new ApiException(HttpStatus.CONFLICT, mensagem);
    }

    public static ApiException proibido(String mensagem) {
        return new ApiException(HttpStatus.FORBIDDEN, mensagem);
    }

    /** Regra de negócio violada (ex.: transição de status inválida). */
    public static ApiException regra(String mensagem) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, mensagem);
    }

    public static ApiException requisicaoInvalida(String mensagem) {
        return new ApiException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
