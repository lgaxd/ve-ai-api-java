package br.com.fiap.veai.relato;

/** Tipos de barreira estrutural. {@code rotulo} é o texto falado nas notificações. */
public enum TipoRelato {
    CALCADA_DANIFICADA("calçada danificada"),
    OBSTRUCAO_PERMANENTE("calçada obstruída"),
    PISO_TATIL("problema no piso tátil"),
    RAMPA("problema na rampa"),
    SEMAFORO_SEM_SOM("semáforo sem som"),
    OBRA_SEM_SINALIZACAO("obra sem sinalização"),
    OUTRO("barreira");

    private final String rotulo;

    TipoRelato(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
