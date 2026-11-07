package br.com.fiap.veai.ia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import br.com.fiap.veai.comum.ApiException;
import br.com.fiap.veai.descricao.ModoDescricao;

/**
 * Descrição por IA generativa com Spring AI: a foto vai como {@code Media} e a resposta volta
 * já convertida para {@link DescricaoIA} (saída estruturada a partir do JSON Schema do record).
 */
@Component
@ConditionalOnProperty(name = "veai.ia.provedor", havingValue = "openai")
public class OpenAiDescritor implements DescritorImagem {

    private static final Logger log = LoggerFactory.getLogger(OpenAiDescritor.class);

    private static final String PEDIDO_AMBIENTE =
            "Descreva o ambiente desta foto para uma pessoa com deficiência visual. Comece pelos perigos.";
    private static final String PEDIDO_TEXTO =
            "Leia o texto desta foto exatamente como está escrito e coloque-o em textoLido. "
                    + "Em resumo, diga em uma frase do que se trata. Também informe perigos visíveis, se houver.";

    private final ChatClient chatClient;

    public OpenAiDescritor(ChatClient.Builder builder,
                           @Value("classpath:prompts/descricao-sistema.txt") Resource promptSistema) {
        this.chatClient = builder.defaultSystem(ler(promptSistema)).build();
    }

    @Override
    public DescricaoIA descrever(ImagemRecebida imagem, ModoDescricao modo) {
        String pedido = modo == ModoDescricao.TEXTO ? PEDIDO_TEXTO : PEDIDO_AMBIENTE;
        try {
            DescricaoIA resposta = chatClient.prompt()
                    .user(u -> u.text(pedido).media(imagem.tipo(), new ByteArrayResource(imagem.bytes())))
                    .call()
                    .entity(DescricaoIA.class);
            if (resposta == null) {
                throw new IllegalStateException("Resposta vazia do modelo");
            }
            return resposta.normalizada();
        } catch (RuntimeException e) {
            log.warn("Falha ao descrever imagem ({}): {}", imagem, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Não consegui descrever a foto agora. Tente de novo em alguns segundos.");
        }
    }

    @Override
    public String responder(ImagemRecebida imagem, String pergunta) {
        try {
            return chatClient.prompt()
                    .user(u -> u.text("Responda em até duas frases, sem inventar nada que não esteja na foto: " + pergunta)
                            .media(imagem.tipo(), new ByteArrayResource(imagem.bytes())))
                    .call()
                    .content();
        } catch (RuntimeException e) {
            log.warn("Falha na pergunta livre ({}): {}", imagem, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Não consegui responder agora. Tente de novo.");
        }
    }

    @Override
    public String provedor() {
        return "openai";
    }

    private static String ler(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
