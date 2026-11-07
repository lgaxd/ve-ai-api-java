package br.com.fiap.veai.ia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import br.com.fiap.veai.config.VeAiProperties;
import br.com.fiap.veai.mensageria.EventoRelato;
import br.com.fiap.veai.relato.Gravidade;
import br.com.fiap.veai.relato.RelatoRepository;
import br.com.fiap.veai.relato.TipoRelato;

/**
 * Enriquecimento OPCIONAL pelo serviço RAG do Pedro (NBR 9050): chamado de forma assíncrona depois que o
 * relato é criado, com timeout curto. Desligado quando {@code veai.rag.url} está vazia; se o serviço
 * falhar, o relato segue com {@code normaReferencia = null}. Ninguém espera ninguém.
 */
@Component
public class RagClassificador {

    private static final Logger log = LoggerFactory.getLogger(RagClassificador.class);

    private final VeAiProperties.Rag config;
    private final RelatoRepository relatos;
    private final TransactionTemplate transacao;
    private final RestClient http;

    public RagClassificador(VeAiProperties properties, RelatoRepository relatos, TransactionTemplate transacao,
                            RestClient.Builder builder) {
        this.config = properties.rag();
        this.relatos = relatos;
        this.transacao = transacao;
        this.http = config.habilitado()
                ? builder.baseUrl(config.url())
                        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings.defaults()
                                .withConnectTimeout(config.timeout()).withReadTimeout(config.timeout())))
                        .build()
                : null;
    }

    public record ClassificarRequest(String texto, TipoRelato tipoSugerido, Gravidade gravidadeSugerida) {}

    public record ClassificarResposta(TipoRelato tipo, Gravidade gravidade, String normaReferencia, String trecho,
                                      String justificativa) {}

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoCriarRelato(EventoRelato evento) {
        if (http == null || evento.tipo() != EventoRelato.Tipo.CRIADO) {
            return;
        }
        try {
            var pedido = transacao.execute(s -> relatos.findById(evento.relatoId())
                    .map(r -> new ClassificarRequest(r.getTexto(), r.getTipo(), r.getGravidade()))
                    .orElse(null));
            if (pedido == null) {
                return;
            }
            ClassificarResposta resposta = http.post().uri("/classificar").body(pedido).retrieve()
                    .body(ClassificarResposta.class);
            if (resposta == null || resposta.normaReferencia() == null || resposta.normaReferencia().isBlank()) {
                return;
            }
            // RELATO.norma_referencia é VARCHAR2(200) no Oracle (mesmo DDL do repositório database)
            String norma = resposta.normaReferencia().length() > 200
                    ? resposta.normaReferencia().substring(0, 200) : resposta.normaReferencia();
            transacao.executeWithoutResult(s -> relatos.findById(evento.relatoId())
                    .ifPresent(r -> r.definirNormaReferencia(norma)));
            log.info("Relato {} enriquecido pelo RAG: {}", evento.relatoId(), norma);
        } catch (RuntimeException e) {
            log.warn("RAG indisponível para o relato {} (segue sem norma): {}", evento.relatoId(), e.getMessage());
        }
    }
}
