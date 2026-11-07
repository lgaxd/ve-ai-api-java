package br.com.fiap.veai.comum;

/** Rate limit estourado: vira HTTP 429 com {@code Retry-After}. */
public class LimiteExcedidoException extends RuntimeException {

    private final long segundosParaLiberar;

    public LimiteExcedidoException(long segundosParaLiberar) {
        super("Muitas fotos em pouco tempo. Aguarde " + segundosParaLiberar + " segundos e tente de novo.");
        this.segundosParaLiberar = segundosParaLiberar;
    }

    public long segundosParaLiberar() {
        return segundosParaLiberar;
    }
}
