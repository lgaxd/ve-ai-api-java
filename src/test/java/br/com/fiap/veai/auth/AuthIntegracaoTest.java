package br.com.fiap.veai.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import br.com.fiap.veai.IntegracaoTest;

class AuthIntegracaoTest extends IntegracaoTest {

    @Test
    @DisplayName("cadastro cria CIDADAO; e-mail repetido dá 409")
    void cadastro() throws Exception {
        String corpo = """
                {"nome":"Joana Prado","email":"joana.prado@teste.dev","senha":"Senha@123"}""";
        mvc.perform(post("/auth/registrar").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("CIDADAO"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
        mvc.perform(post("/auth/registrar").contentType(MediaType.APPLICATION_JSON).content(corpo.replace("joana", "JOANA")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Já existe uma conta com este e-mail."));
    }

    @Test
    @DisplayName("validação devolve 400 com os campos inválidos")
    void validacao() throws Exception {
        mvc.perform(post("/auth/registrar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\",\"email\":\"nao-e-email\",\"senha\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.campos[*].campo", hasItem("nome")))
                .andExpect(jsonPath("$.campos[*].campo", hasItem("email")))
                .andExpect(jsonPath("$.campos[*].campo", hasItem("senha")));
    }

    @Test
    @DisplayName("login devolve JWT e papel; senha errada dá 401 com mensagem genérica")
    void login() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + MARINA + "\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.papel").value("GESTOR"))
                .andExpect(jsonPath("$.usuario.regioes[*].nome", hasItem("Sé")));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ANA + "\",\"senha\":\"errada123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha incorretos."));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ninguem@teste.dev\",\"senha\":\"qualquer123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha incorretos."));
    }

    @Test
    @DisplayName("sem token, com token inválido ou adulterado: 401 no formato de erro da API")
    void semToken() throws Exception {
        mvc.perform(get("/usuarios/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/usuarios/me").header(auth(), "Bearer abc.def.ghi")).andExpect(status().isUnauthorized());
        String token = login(ANA);
        String adulterado = token.substring(0, token.length() - 4) + "AAAA";
        mvc.perform(get("/usuarios/me").header(auth(), bearer(adulterado))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("preferências: atualiza e valida a faixa da velocidade da voz")
    void preferencias() throws Exception {
        String token = novoCidadao();
        mvc.perform(put("/usuarios/me/preferencias").header(auth(), bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"velocidadeVoz\":1.5,\"perguntaLivreAtiva\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.velocidadeVoz").value(1.5))
                .andExpect(jsonPath("$.perguntaLivreAtiva").value(true));
        mvc.perform(put("/usuarios/me/preferencias").header(auth(), bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"velocidadeVoz\":3.0,\"perguntaLivreAtiva\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("velocidadeVoz"));
    }

    @Test
    @DisplayName("rotas públicas: health e Swagger sem token")
    void publicas() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/relatos']").exists());
        mvc.perform(get("/regioes")).andExpect(status().isUnauthorized());
    }
}
