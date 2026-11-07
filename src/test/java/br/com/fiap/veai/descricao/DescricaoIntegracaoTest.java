package br.com.fiap.veai.descricao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import br.com.fiap.veai.IntegracaoTest;
import br.com.fiap.veai.ia.DescritorFalso;

/** Usa o DescritorFalso: cada "foto" escolhe um cenário fixo (0 = barreira, 1 = só perigo momentâneo, 3 = escura). */
class DescricaoIntegracaoTest extends IntegracaoTest {

    @Test
    @DisplayName("barreira com confiança alta traz a sugestão de relato; perigos vêm antes do resumo")
    void sugereRelato() throws Exception {
        String token = novoCidadao();
        descrever(token, foto(0), "AMBIENTE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perigos[0].descricao").value("poste no meio da calçada"))
                .andExpect(jsonPath("$.barreiraDetectada").value(true))
                .andExpect(jsonPath("$.barreiraTipo").value("OBSTRUCAO_PERMANENTE"))
                .andExpect(jsonPath("$.sugestaoRelato.pergunta").value("Parece haver calçada obstruída. Quer avisar a rede de apoio?"))
                .andExpect(jsonPath("$.util").isEmpty());
    }

    @Test
    @DisplayName("perigo momentâneo e foto escura (confiança baixa) nunca sugerem relato")
    void naoSugere() throws Exception {
        String token = novoCidadao();
        descrever(token, foto(1), "AMBIENTE").andExpect(status().isCreated())
                .andExpect(jsonPath("$.perigos[0].descricao").value("bicicleta se aproximando"))
                .andExpect(jsonPath("$.barreiraDetectada").value(false))
                .andExpect(jsonPath("$.sugestaoRelato").isEmpty());
        descrever(token, foto(3), "AMBIENTE").andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumo").value("Não consegui ver bem: a foto está escura."))
                .andExpect(jsonPath("$.sugestaoRelato").isEmpty());
    }

    @Test
    @DisplayName("modo TEXTO guarda o texto lido; formato inválido 415; sem foto 400")
    void modoTextoEFormatos() throws Exception {
        String token = novoCidadao();
        descrever(token, foto(2), "TEXTO").andExpect(status().isCreated())
                .andExpect(jsonPath("$.modo").value("TEXTO"))
                .andExpect(jsonPath("$.descricaoCompleta").value("Farmácia São Paulo. Aberta 24 horas. Entrada pela lateral."));
        descrever(token, new MockMultipartFile("imagem", "x.gif", "image/jpeg", "GIF89a....".getBytes()), "AMBIENTE")
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/descricoes").header(auth(), bearer(token))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("histórico paginado, avaliação, exclusão e isolamento entre usuários")
    void historico() throws Exception {
        String token = novoCidadao();
        Integer primeira = ler(descrever(token, foto(0), "AMBIENTE").andReturn(), "$.id");
        Integer segunda = ler(descrever(token, foto(1), "AMBIENTE").andReturn(), "$.id");

        mvc.perform(get("/descricoes?size=1").header(auth(), bearer(token)))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.conteudo[0].id").value(segunda));

        mvc.perform(patch("/descricoes/" + primeira + "/avaliacao").header(auth(), bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"util\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.util").value(true));
        mvc.perform(patch("/descricoes/" + primeira + "/avaliacao").header(auth(), bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        String outro = novoCidadao();
        mvc.perform(get("/descricoes/" + primeira).header(auth(), bearer(outro))).andExpect(status().isNotFound());
        mvc.perform(delete("/descricoes/" + primeira).header(auth(), bearer(outro))).andExpect(status().isNotFound());

        // Relato criado a partir da descrição continua existindo depois que a descrição é apagada
        double[] ponto = pontoNovo(SE);
        MvcResult relato = mvc.perform(post("/relatos").header(auth(), bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"OBSTRUCAO_PERMANENTE\",\"gravidade\":\"MEDIA\",\"texto\":\"Poste\",\"latitude\":"
                                + ponto[0] + ",\"longitude\":" + ponto[1] + ",\"descricaoId\":" + primeira + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.relato.descricaoId").value(primeira)).andReturn();
        Integer relatoId = ler(relato, "$.relato.id");
        mvc.perform(delete("/descricoes/" + primeira).header(auth(), bearer(token))).andExpect(status().isNoContent());
        mvc.perform(get("/relatos/" + relatoId).header(auth(), bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descricaoId").isEmpty());
    }

    @Test
    @DisplayName("pergunta livre: bloqueada até ativar nas preferências; depois responde sem gravar nada")
    void perguntaLivre() throws Exception {
        String token = novoCidadao();
        Integer id = ler(descrever(token, foto(0), "AMBIENTE").andReturn(), "$.id");
        perguntar(token, id).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value("Ative a pergunta livre por voz nas preferências."));

        mvc.perform(put("/usuarios/me/preferencias").header(auth(), bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"velocidadeVoz\":1.0,\"perguntaLivreAtiva\":true}")).andExpect(status().isOk());
        perguntar(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.resposta").isNotEmpty());
        mvc.perform(get("/descricoes").header(auth(), bearer(token))).andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("rate limit: acima de 5 fotos por minuto (perfil de teste) responde 429 com Retry-After")
    void rateLimit() throws Exception {
        String token = novoCidadao();
        for (int i = 0; i < 5; i++) {
            descrever(token, foto(1), "AMBIENTE").andExpect(status().isCreated());
        }
        descrever(token, foto(1), "AMBIENTE")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
        // Outro usuário não é afetado
        descrever(novoCidadao(), foto(1), "AMBIENTE").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("gestor não usa a descrição (rota de cidadão)")
    void gestorNaoDescreve() throws Exception {
        descrever(login(MARINA), foto(0), "AMBIENTE").andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions descrever(String token, MockMultipartFile foto, String modo)
            throws Exception {
        return mvc.perform(multipart("/descricoes").file(foto).param("modo", modo).header(auth(), bearer(token)));
    }

    private org.springframework.test.web.servlet.ResultActions perguntar(String token, Integer id) throws Exception {
        return mvc.perform(multipart("/descricoes/" + id + "/pergunta").file(foto(0)).param("pergunta", "Tem degrau à frente?")
                .header(auth(), bearer(token)));
    }

    /** JPEG mínimo (assinatura FF D8 FF) cujo conteúdo leva o DescritorFalso ao cenário pedido. */
    private static MockMultipartFile foto(int cenario) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        for (int semente = 0; ; semente++) {
            Arrays.fill(bytes, 3, bytes.length, (byte) semente);
            if (DescritorFalso.cenarioDe(bytes) == cenario) {
                return new MockMultipartFile("imagem", "foto.jpg", "image/jpeg", bytes.clone());
            }
        }
    }
}
