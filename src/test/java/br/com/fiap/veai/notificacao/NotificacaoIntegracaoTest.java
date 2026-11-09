package br.com.fiap.veai.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.fiap.veai.IntegracaoTest;
import br.com.fiap.veai.mensageria.EventoRelato;
import br.com.fiap.veai.mensageria.RabbitConfig;
import br.com.fiap.veai.relato.RelatoRepository;

/** Fluxo assíncrono de ponta a ponta: API → exchange veai.relatos → fila → @RabbitListener → NOTIFICACAO. */
class NotificacaoIntegracaoTest extends IntegracaoTest {

    @Autowired
    RabbitAdmin rabbitAdmin;

    @Autowired
    NotificacaoService notificacoes;

    @Autowired
    RelatoRepository relatos;

    @Autowired
    PlatformTransactionManager transacoes;

    @Test
    @DisplayName("consumidor espera a exclusão em andamento do relato e ignora o evento (sem ORA-02291 nem deadlock)")
    void consumidorEsperaExclusaoDoRelato() throws Exception {
        String autor = novoCidadao();
        String marina = login(MARINA);
        MvcResult criado = mvc.perform(post("/relatos").header(auth(), bearer(autor)).contentType(MediaType.APPLICATION_JSON)
                        .content(relatoJson("OUTRO", "BAIXA", pontoNovo(SE))))
                .andExpect(status().isCreated()).andReturn();
        long id = ((Integer) ler(criado, "$.relato.id")).longValue();
        aguardar(() -> !mensagens(marina, (int) id).isEmpty()); // o consumidor real já terminou

        // Exclusão do autor em andamento: relato travado e apagado, commit só daqui a 1 s
        CountDownLatch apagado = new CountDownLatch(1);
        CompletableFuture<Void> exclusao = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transacoes).executeWithoutResult(t -> {
                    relatos.buscarParaAtualizar(id).ifPresent(relatos::delete);
                    relatos.flush();
                    apagado.countDown();
                    esperar(1000);
                }));
        assertThat(apagado.await(10, TimeUnit.SECONDS)).isTrue();

        // O evento chega no meio da exclusão. Antes da correção, o consumidor lia o relato (ainda visível),
        // tentava inserir a notificação e falhava com ORA-02291, ou com ORA-00060 (deadlock) se houvesse
        // mais de um destinatário. Agora ele espera o DELETE terminar e ignora o relato que não existe mais.
        long inicio = System.nanoTime();
        int criadas = notificacoes.gerar(EventoRelato.criado(id, 0L, Instant.now()));
        Duration esperou = Duration.ofNanos(System.nanoTime() - inicio);
        exclusao.get(10, TimeUnit.SECONDS);

        assertThat(criadas).isZero();
        assertThat(esperou).isGreaterThan(Duration.ofMillis(500));
        assertThat(relatos.findById(id)).isEmpty();
    }

    private static void esperar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

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
