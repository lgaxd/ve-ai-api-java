package br.com.fiap.veai.relato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import br.com.fiap.veai.IntegracaoTest;

class RelatoIntegracaoTest extends IntegracaoTest {

    @Autowired
    RelatoRepository relatos;

    @Test
    @DisplayName("cria o relato (201), escolhendo a região pelo centro mais próximo")
    void cria() throws Exception {
        String token = novoCidadao();
        double[] ponto = pontoNovo(VILA_MARIANA);
        mvc.perform(post("/relatos").header(auth(), bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(relatoJson("RAMPA", "ALTA", ponto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value("CRIADO"))
                .andExpect(jsonPath("$.apoioRegistrado").value(false))
                .andExpect(jsonPath("$.relato.status").value("ABERTO"))
                .andExpect(jsonPath("$.relato.regiao.nome").value("Vila Mariana"))
                .andExpect(jsonPath("$.relato.prioridade").value(5));
    }

    @Test
    @DisplayName("regra dos 30 m: outro cidadão a 15 m vira +1 apoio; o autor e quem já apoiou recebem JA_REGISTRADO")
    void deduplicacao() throws Exception {
        String autor = novoCidadao();
        String outro = novoCidadao();
        double[] ponto = pontoNovo(SE);
        MvcResult criado = registrar(autor, "CALCADA_DANIFICADA", "MEDIA", ponto, 201);
        Integer id = ler(criado, "$.relato.id");

        mvc.perform(post("/relatos").header(auth(), bearer(outro)).contentType(MediaType.APPLICATION_JSON)
                        .content(relatoJson("CALCADA_DANIFICADA", "ALTA", aMetros(ponto, 15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("APOIO_REGISTRADO"))
                .andExpect(jsonPath("$.apoioRegistrado").value(true))
                .andExpect(jsonPath("$.relato.id").value(id))
                .andExpect(jsonPath("$.relato.totalApoios").value(1))
                .andExpect(jsonPath("$.relato.prioridade").value(6)); // 3 × (1 + 1)

        registrarEsperando(outro, "CALCADA_DANIFICADA", aMetros(ponto, 5), "JA_REGISTRADO", id);
        registrarEsperando(autor, "CALCADA_DANIFICADA", ponto, "JA_REGISTRADO", id);
        assertThat(relatos.findById(id.longValue())).get().extracting(Relato::getTotalApoios).isEqualTo(1);
    }

    @Test
    @DisplayName("tipo diferente no mesmo lugar, ou o mesmo tipo a 50 m, cria outro relato")
    void naoDeduplica() throws Exception {
        String a = novoCidadao();
        String b = novoCidadao();
        double[] ponto = pontoNovo(SE);
        Integer id = ler(registrar(a, "PISO_TATIL", "MEDIA", ponto, 201), "$.relato.id");
        Integer outroTipo = ler(registrar(b, "SEMAFORO_SEM_SOM", "MEDIA", ponto, 201), "$.relato.id");
        Integer longe = ler(registrar(b, "PISO_TATIL", "MEDIA", aMetros(ponto, 50), 201), "$.relato.id");
        assertThat(outroTipo).isNotEqualTo(id);
        assertThat(longe).isNotEqualTo(id);
    }

    @Test
    @DisplayName("relato finalizado não recebe apoio: um novo é criado no mesmo ponto")
    void finalizadoNaoDeduplica() throws Exception {
        String a = novoCidadao();
        String b = novoCidadao();
        String marina = login(MARINA);
        double[] ponto = pontoNovo(SE);
        Integer id = ler(registrar(a, "OUTRO", "BAIXA", ponto, 201), "$.relato.id");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/gestor/relatos/" + id + "/status")
                        .header(auth(), bearer(marina)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DESCARTADO\",\"motivoDescarte\":\"Já removido\"}"))
                .andExpect(status().isOk());
        Integer novo = ler(registrar(b, "OUTRO", "BAIXA", ponto, 201), "$.relato.id");
        assertThat(novo).isNotEqualTo(id);
    }

    @Test
    @DisplayName("Idempotency-Key: repetir devolve a mesma resposta sem duplicar; outro conteúdo dá 422")
    void idempotencia() throws Exception {
        String token = novoCidadao();
        double[] ponto = pontoNovo(SE);
        String corpo = relatoJson("OBRA_SEM_SINALIZACAO", "ALTA", ponto);
        long antes = relatos.count();

        MvcResult primeira = mvc.perform(post("/relatos").header(auth(), bearer(token)).header("Idempotency-Key", "envio-123")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replayed")).andReturn();
        MvcResult repetida = mvc.perform(post("/relatos").header(auth(), bearer(token)).header("Idempotency-Key", "envio-123")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replayed", "true")).andReturn();

        assertThat(repetida.getResponse().getContentAsString()).isEqualTo(primeira.getResponse().getContentAsString());
        assertThat(relatos.count()).isEqualTo(antes + 1);

        mvc.perform(post("/relatos").header(auth(), bearer(token)).header("Idempotency-Key", "envio-123")
                        .contentType(MediaType.APPLICATION_JSON).content(relatoJson("RAMPA", "ALTA", ponto)))
                .andExpect(status().isUnprocessableContent());

        // A mesma chave de OUTRO usuário é independente
        mvc.perform(post("/relatos").header(auth(), bearer(novoCidadao())).header("Idempotency-Key", "envio-123")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("APOIO_REGISTRADO"));
    }

    @Test
    @DisplayName("acompanhar, editar e excluir: regras do autor")
    void cicloDoAutor() throws Exception {
        String autor = novoCidadao();
        String estranho = novoCidadao();
        double[] ponto = pontoNovo(SE);
        Integer id = ler(registrar(autor, "RAMPA", "BAIXA", ponto, 201), "$.relato.id");

        mvc.perform(get("/relatos/meus").header(auth(), bearer(autor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].id").value(id))
                .andExpect(jsonPath("$.pagina").value(0));
        mvc.perform(get("/relatos/" + id).header(auth(), bearer(estranho))).andExpect(status().isNotFound());

        mvc.perform(put("/relatos/" + id).header(auth(), bearer(autor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"RAMPA\",\"gravidade\":\"ALTA\",\"texto\":\"Rampa sem corrimão\",\"endereco\":\"Rua Nova, 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gravidade").value("ALTA"))
                .andExpect(jsonPath("$.prioridade").value(5))
                .andExpect(jsonPath("$.latitude").value(ponto[0]));
        mvc.perform(put("/relatos/" + id).header(auth(), bearer(estranho)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"RAMPA\",\"gravidade\":\"ALTA\",\"texto\":\"x\"}"))
                .andExpect(status().isNotFound());

        // Com o apoio de outra pessoa o relato não pode mais ser excluído; o apoiador passa a poder acompanhar
        registrarEsperando(estranho, "RAMPA", ponto, "APOIO_REGISTRADO", id);
        mvc.perform(get("/relatos/" + id).header(auth(), bearer(estranho))).andExpect(status().isOk());
        mvc.perform(delete("/relatos/" + id).header(auth(), bearer(autor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Outras pessoas já confirmaram este relato. Ele não pode ser excluído."));

        Integer semApoio = ler(registrar(autor, "OUTRO", "BAIXA", pontoNovo(SE), 201), "$.relato.id");
        mvc.perform(delete("/relatos/" + semApoio).header(auth(), bearer(autor))).andExpect(status().isNoContent());
        mvc.perform(get("/relatos/" + semApoio).header(auth(), bearer(autor))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("validação, papel e JSON inválido")
    void errosDeEntrada() throws Exception {
        String token = novoCidadao();
        mvc.perform(post("/relatos").header(auth(), bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"RAMPA\",\"gravidade\":\"ALTA\",\"texto\":\"\",\"latitude\":120}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.length()").value(3)); // texto, latitude, longitude
        mvc.perform(post("/relatos").header(auth(), bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"BURACO\",\"gravidade\":\"ALTA\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/relatos").header(auth(), bearer(login(MARINA))).contentType(MediaType.APPLICATION_JSON)
                        .content(relatoJson("RAMPA", "ALTA", pontoNovo(SE))))
                .andExpect(status().isForbidden());
    }

    private MvcResult registrar(String token, String tipo, String gravidade, double[] ponto, int statusEsperado)
            throws Exception {
        return mvc.perform(post("/relatos").header(auth(), bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(relatoJson(tipo, gravidade, ponto)))
                .andExpect(status().is(statusEsperado)).andReturn();
    }

    private void registrarEsperando(String token, String tipo, double[] ponto, String resultado, Integer id) throws Exception {
        mvc.perform(post("/relatos").header(auth(), bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(relatoJson(tipo, "MEDIA", ponto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value(resultado))
                .andExpect(jsonPath("$.relato.id").value(id));
    }
}
