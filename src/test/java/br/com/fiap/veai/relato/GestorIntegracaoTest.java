package br.com.fiap.veai.relato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import br.com.fiap.veai.IntegracaoTest;

class GestorIntegracaoTest extends IntegracaoTest {

    @Test
    @DisplayName("fila: só as regiões do gestor, ordenada por prioridade; filtros por status e região")
    void fila() throws Exception {
        String marina = login(MARINA);
        MvcResult r = mvc.perform(get("/gestor/relatos?size=50").header(auth(), bearer(marina)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[*].regiao.nome", everyItem(oneOf("Pinheiros", "Sé"))))
                .andReturn();
        List<Integer> prioridades = ler(r, "$.conteudo[*].prioridade");
        assertThat(prioridades).isNotEmpty().isSortedAccordingTo((a, b) -> b - a);

        mvc.perform(get("/gestor/relatos?status=ENCAMINHADO&regiaoId=2").header(auth(), bearer(marina)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[*].status", everyItem(is("ENCAMINHADO"))))
                .andExpect(jsonPath("$.conteudo[*].regiao.nome", everyItem(is("Sé"))));

        mvc.perform(get("/gestor/relatos?size=2").header(auth(), bearer(marina)))
                .andExpect(jsonPath("$.tamanho").value(2))
                .andExpect(jsonPath("$.conteudo.length()").value(2));
    }

    @Test
    @DisplayName("gestor fora da região: 403 na fila, no detalhe, no status e no histórico; cidadão: 403")
    void autorizacaoPorRegiao() throws Exception {
        String marina = login(MARINA);
        String rafael = login(RAFAEL);
        Integer idVilaMariana = criarRelato(VILA_MARIANA);

        mvc.perform(get("/gestor/relatos?regiaoId=3").header(auth(), bearer(marina))).andExpect(status().isForbidden());
        mvc.perform(get("/gestor/relatos/" + idVilaMariana).header(auth(), bearer(marina))).andExpect(status().isForbidden());
        mvc.perform(get("/gestor/relatos/" + idVilaMariana + "/historico").header(auth(), bearer(marina)))
                .andExpect(status().isForbidden());
        mudar(marina, idVilaMariana, "{\"status\":\"EM_TRIAGEM\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensagem").value("Este relato é de uma região que você não atende."));

        mvc.perform(get("/gestor/relatos/" + idVilaMariana).header(auth(), bearer(rafael))).andExpect(status().isOk());
        mvc.perform(get("/gestor/relatos").header(auth(), bearer(login(ANA)))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("máquina de estados: fluxo completo, transições inválidas e campos obrigatórios")
    void maquinaDeEstados() throws Exception {
        String marina = login(MARINA);
        Integer id = criarRelato(SE);

        mudar(marina, id, "{\"status\":\"RESOLVIDO\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value("Não é possível mudar o relato de ABERTO para RESOLVIDO."));
        mudar(marina, id, "{\"status\":\"EM_TRIAGEM\",\"comentario\":\"Vistoria agendada\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_TRIAGEM"));
        mudar(marina, id, "{\"status\":\"ABERTO\"}").andExpect(status().isUnprocessableContent());
        mudar(marina, id, "{\"status\":\"ENCAMINHADO\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value("Informe o protocolo do órgão para encaminhar o relato."));
        mudar(marina, id, "{\"status\":\"ENCAMINHADO\",\"protocoloOrgao\":\"SP156-2026-1\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.protocoloOrgao").value("SP156-2026-1"));
        mudar(marina, id, "{\"status\":\"RESOLVIDO\",\"comentario\":\"Rampa refeita\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.prioridade").value(0));
        mudar(marina, id, "{\"status\":\"DESCARTADO\",\"motivoDescarte\":\"x\"}").andExpect(status().isUnprocessableContent());

        mvc.perform(get("/gestor/relatos/" + id + "/historico").header(auth(), bearer(marina)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].statusAnterior").value("ABERTO"))
                .andExpect(jsonPath("$[0].comentario").value("Vistoria agendada"))
                .andExpect(jsonPath("$[2].statusNovo").value("RESOLVIDO"))
                .andExpect(jsonPath("$[2].gestorNome").value("Marina Alves"));

        Integer outro = criarRelato(SE);
        mudar(marina, outro, "{\"status\":\"DESCARTADO\"}").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensagem").value("Informe o motivo do descarte."));
        mudar(marina, outro, "{\"status\":\"PENDENTE\"}").andExpect(status().isBadRequest());
        mudar(marina, 999_999, "{\"status\":\"EM_TRIAGEM\"}").andExpect(status().isNotFound());
    }

    private Integer criarRelato(double[] regiao) throws Exception {
        MvcResult r = mvc.perform(post("/relatos").header(auth(), bearer(novoCidadao())).contentType(MediaType.APPLICATION_JSON)
                        .content(relatoJson("RAMPA", "MEDIA", pontoNovo(regiao))))
                .andExpect(status().isCreated()).andReturn();
        return ler(r, "$.relato.id");
    }

    private org.springframework.test.web.servlet.ResultActions mudar(String token, Integer id, String corpo) throws Exception {
        return mvc.perform(patch("/gestor/relatos/" + id + "/status").header(auth(), bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }
}
