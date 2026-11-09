package br.com.fiap.veai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/**
 * Base dos testes de integração: aplicação inteira + Oracle XE e RabbitMQ reais (Testcontainers).
 * O contexto (e os containers) é compartilhado entre as classes de teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegracaoTest {

    /** Usuários do seed (senha Veai@2026). Marina atende Pinheiros (1) e Sé (2); Rafael, Vila Mariana (3). */
    protected static final String ANA = "ana.souza@veai.dev";
    protected static final String BRUNO = "bruno.lima@veai.dev";
    protected static final String MARINA = "marina.alves@ong-veai.dev";
    protected static final String RAFAEL = "rafael.costa@cras-veai.dev";
    protected static final String SENHA = "Veai@2026";

    /** Centros das regiões do seed. */
    protected static final double[] SE = {-23.5503, -46.6339};
    protected static final double[] VILA_MARIANA = {-23.5889, -46.6355};

    private static final AtomicInteger PONTOS = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    protected String login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Login(email, SENHA))))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.token");
    }

    /** Registra um cidadão novo (isolamento entre testes) e devolve o token. */
    protected String novoCidadao() throws Exception {
        String email = "cidadao-" + UUID.randomUUID() + "@teste.dev";
        mvc.perform(post("/auth/registrar").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Registro("Cidadão de Teste", email, SENHA))))
                .andExpect(status().isCreated());
        return login(email);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected static String auth() {
        return HttpHeaders.AUTHORIZATION;
    }

    /**
     * Ponto único perto do centro da região: cada chamada anda ~110 m para o leste e um pouco para o sul,
     * então relatos de testes diferentes nunca caem a menos de 30 m uns dos outros (nem dos do seed).
     */
    protected static double[] pontoNovo(double[] centro) {
        int n = PONTOS.incrementAndGet();
        return new double[]{seisCasas(centro[0] - 0.004 - (n % 10) * 0.0011), seisCasas(centro[1] + 0.006 + (n / 10) * 0.0011)};
    }

    /**
     * Mesma precisão da coluna NUMBER(9,6): sem isso, uma conta como -23.5503 - 0.0128 vira -23.563100000000002
     * e não bate com o -23.5631 que o Oracle devolve.
     */
    private static double seisCasas(double valor) {
        return Math.round(valor * 1_000_000d) / 1_000_000d;
    }

    /** Desloca um ponto alguns metros para o norte (1 m ≈ 0,000009°). */
    protected static double[] aMetros(double[] ponto, double metros) {
        return new double[]{ponto[0] + metros * 0.000009, ponto[1]};
    }

    protected String relatoJson(String tipo, String gravidade, double[] ponto) throws Exception {
        return json.writeValueAsString(new RelatoReq(tipo, gravidade, "Barreira de teste", ponto[0], ponto[1], "Rua de Teste, 1"));
    }

    protected static <T> T ler(MvcResult r, String caminho) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), caminho);
    }

    /** Espera algo assíncrono (consumidor RabbitMQ) acontecer, por até 15 s. */
    protected static void aguardar(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            if (condicao.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("A condição não aconteceu em 15 s");
    }

    record Login(String email, String senha) {}

    record Registro(String nome, String email, String senha) {}

    record RelatoReq(String tipo, String gravidade, String texto, double latitude, double longitude, String endereco) {}
}
