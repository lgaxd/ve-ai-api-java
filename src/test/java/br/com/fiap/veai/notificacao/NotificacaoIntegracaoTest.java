package br.com.fiap.veai.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import br.com.fiap.veai.IntegracaoTest;
import br.com.fiap.veai.mensageria.RabbitConfig;

/** Fluxo assíncrono de ponta a ponta: API → exchange veai.relatos → fila → @RabbitListener → NOTIFICACAO. */
class NotificacaoIntegracaoTest extends IntegracaoTest {

    @Autowired
    RabbitAdmin rabbitAdmin;

    @Test
    @DisplayName("criar, apoiar e mudar status notifica gestores, autor e apoiadores com mensagens para voz")
    void fluxoCompleto() throws Exception {
        String autor = novoCidadao();
        String apoiador = novoCidadao();
        String marina = login(MARINA);
        double[] ponto = pontoNovo(SE);

        MvcResult criado = mvc.perform(post("/relatos").header(auth(), bearer(autor)).contentType(MediaType.APPLICATION_JSON)
                        .content(relatoJson("SEMAFORO_SEM_SOM", "MEDIA", ponto)))
                .andExpect(status().isCreated()).andReturn();
        Integer id = ler(criado, "$.relato.id");
        aguardar(() -> mensagens(marina, id).contains("Novo relato de semáforo sem som em Sé."));

        mvc.perform(post("/relatos").header(auth(), bearer(apoiador)).contentType(MediaType.APPLICATION_JSON)
                        .content(relatoJson("SEMAFORO_SEM_SOM", "MEDIA", aMetros(ponto, 10))))
                .andExpect(status().isOk());
        aguardar(() -> mensagens(autor, id).contains(
                "Mais uma pessoa confirmou seu relato de semáforo sem som. Agora 1 pessoa já confirmou."));

        mvc.perform(patch("/gestor/relatos/" + id + "/status").header(auth(), bearer(marina))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EM_TRIAGEM\"}"))
                .andExpect(status().isOk());
        aguardar(() -> mensagens(autor, id).contains("Seu relato de semáforo sem som está em análise pela rede de apoio."));
        aguardar(() -> mensagens(apoiador, id).contains(
                "O relato de semáforo sem som que você apoiou está em análise pela rede de apoio."));

        // Só a notificação de status para o apoiador; nenhuma notificação vai para quem não participa
        assertThat(mensagens(apoiador, id)).hasSize(1);
        assertThat(mensagens(novoCidadao(), id)).isEmpty();
        assertThat(rabbitAdmin.getQueueInfo(RabbitConfig.DLQ).getMessageCount()).isZero();
    }

    @Test
    @DisplayName("marcar como lida, filtrar não lidas e não mexer na notificação de outra pessoa")
    void marcarComoLida() throws Exception {
        String autor = novoCidadao();
        String marina = login(MARINA);
        double[] ponto = pontoNovo(SE);
        MvcResult criado = mvc.perform(post("/relatos").header(auth(), bearer(autor)).contentType(MediaType.APPLICATION_JSON)
                        .content(relatoJson("OUTRO", "BAIXA", ponto)))
                .andExpect(status().isCreated()).andReturn();
        Integer id = ler(criado, "$.relato.id");
        mvc.perform(patch("/gestor/relatos/" + id + "/status").header(auth(), bearer(marina))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DESCARTADO\",\"motivoDescarte\":\"Fora do escopo\"}"));
        aguardar(() -> !mensagens(autor, id).isEmpty());

        MvcResult lista = mvc.perform(get("/notificacoes?lida=false").header(auth(), bearer(autor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[0].mensagem").value("Seu relato de barreira foi encerrado. Motivo: Fora do escopo"))
                .andReturn();
        Integer notificacao = ler(lista, "$.conteudo[0].id");

        mvc.perform(patch("/notificacoes/" + notificacao + "/lida").header(auth(), bearer(novoCidadao())))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/notificacoes/" + notificacao + "/lida").header(auth(), bearer(autor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lida").value(true));
        mvc.perform(get("/notificacoes?lida=false").header(auth(), bearer(autor)))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    private List<String> mensagens(String token, Integer relatoId) {
        try {
            MvcResult r = mvc.perform(get("/notificacoes?size=50").header(auth(), bearer(token))).andReturn();
            return ler(r, "$.conteudo[?(@.relatoId == " + relatoId + ")].mensagem");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
