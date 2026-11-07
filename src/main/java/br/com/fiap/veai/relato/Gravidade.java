package br.com.fiap.veai.relato;

/** Gravidade da barreira e seu peso na fórmula de prioridade (1/3/5). */
public enum Gravidade {
    BAIXA(1), MEDIA(3), ALTA(5);

    private final int peso;

    Gravidade(int peso) {
        this.peso = peso;
    }

    public int peso() {
        return peso;
    }
}
