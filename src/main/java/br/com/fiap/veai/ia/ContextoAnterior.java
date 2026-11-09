package br.com.fiap.veai.ia;

import java.util.List;

/**
 * Modo automático: a foto anterior da caminhada, já descrita. {@code descricaoCompleta} é o que a pessoa
 * ouviu (pode ser só uma confirmação curta); {@code resumo} e {@code perigos} sempre descrevem a cena inteira.
 * Com isso o modelo fala só o que mudou (ou confirma em uma frase) e responde {@code novidade}.
 */
public record ContextoAnterior(String descricaoCompleta, String resumo, List<Perigo> perigos, long segundosAtras) {

    public ContextoAnterior {
        perigos = perigos == null ? List.of() : List.copyOf(perigos);
    }
}
