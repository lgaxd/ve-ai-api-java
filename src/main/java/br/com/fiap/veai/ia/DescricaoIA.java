package br.com.fiap.veai.ia;

import java.util.List;

/**
 * Saída estruturada do modelo (seção 5.1 do escopo). O Spring AI gera o JSON Schema a partir deste record
 * e converte a resposta do modelo diretamente para ele.
 */
public record DescricaoIA(
        List<Perigo> perigos,
        String resumo,
        String descricaoCompleta,
        List<String> objetos,
        String textoLido,
        Barreira barreira) {

    /** Garante listas e barreira não nulas, mesmo que o modelo omita campos. */
    public DescricaoIA normalizada() {
        return new DescricaoIA(
                perigos == null ? List.of() : perigos,
                resumo == null ? "" : resumo,
                descricaoCompleta == null ? "" : descricaoCompleta,
                objetos == null ? List.of() : objetos,
                textoLido,
                barreira == null ? Barreira.nenhuma() : barreira);
    }
}
