package br.com.fiap.veai.ia;

import br.com.fiap.veai.descricao.ModoDescricao;

/**
 * Porta para o modelo de visão. Implementações: {@link OpenAiDescritor} (Spring AI + GPT) e
 * {@link DescritorFalso} (respostas fixas, para desenvolvimento e testes sem gastar créditos).
 * Escolhida pela propriedade {@code veai.ia.provedor}.
 */
public interface DescritorImagem {

    /**
     * Descreve a foto. {@code anterior} vem só no modo automático (null no manual e na primeira foto):
     * com ele, o modelo compara a cena com o que a pessoa já ouviu e preenche {@code novidade}.
     */
    DescricaoIA descrever(ImagemRecebida imagem, ModoDescricao modo, ContextoAnterior anterior);

    /** Pergunta livre por voz sobre a última foto. Nada é gravado. */
    String responder(ImagemRecebida imagem, String pergunta);

    String provedor();
}
