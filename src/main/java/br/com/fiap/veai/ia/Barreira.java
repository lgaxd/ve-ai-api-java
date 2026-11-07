package br.com.fiap.veai.ia;

import br.com.fiap.veai.relato.Gravidade;
import br.com.fiap.veai.relato.TipoRelato;

/** Barreira estrutural identificada pela IA, com a confiança (0 a 1) usada para sugerir um relato. */
public record Barreira(boolean detectada, TipoRelato tipo, Gravidade gravidade, double confianca, String justificativa) {

    public static Barreira nenhuma() {
        return new Barreira(false, null, null, 0d, null);
    }
}
