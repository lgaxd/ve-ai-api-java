package br.com.fiap.veai.ia;

import br.com.fiap.veai.descricao.ModoDescricao;

/**
 * Porta para o modelo de visão. Implementações: {@link OpenAiDescritor} (Spring AI + GPT) e
 * {@link DescritorFalso} (respostas fixas, para desenvolvimento e testes sem gastar créditos).
 * Escolhida pela propriedade {@code veai.ia.provedor}.
 */
public interface DescritorImagem {

    DescricaoIA descrever(ImagemRecebida imagem, ModoDescricao modo);

    /** Pergunta livre por voz sobre a última foto. Nada é gravado. */
    String responder(ImagemRecebida imagem, String pergunta);

    String provedor();
}
