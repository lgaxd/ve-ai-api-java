package br.com.fiap.veai.relato;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.fiap.veai.comum.ApiException;
import br.com.fiap.veai.config.VeAiProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotência do {@code POST /relatos} pelo header {@code Idempotency-Key}.
 * A ação e o registro da resposta acontecem na MESMA transação: se duas requisições com a mesma chave
 * chegarem juntas, a segunda viola a chave única, é desfeita por inteiro e devolve a resposta da primeira.
 */
@Service
public class IdempotenciaService {

    private static final Logger log = LoggerFactory.getLogger(IdempotenciaService.class);
    private static final int TAMANHO_MAXIMO_CHAVE = 100;

    private final RegistroIdempotenciaRepository repository;
    private final JsonMapper json;
    private final Clock clock;
    private final Duration validade;
    private final TransactionTemplate transacao;

    public IdempotenciaService(RegistroIdempotenciaRepository repository, JsonMapper json, Clock clock,
                               VeAiProperties properties, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.json = json;
        this.clock = clock;
        this.validade = properties.relato().validadeIdempotencia();
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public record Resposta<T>(int status, T corpo, boolean repetida) {}

    public <T> Resposta<T> executar(Long usuarioId, String chave, Object requisicao, Class<T> tipo,
                                    Supplier<Resposta<T>> acao) {
        if (chave == null || chave.isBlank()) {
            return acao.get();
        }
        String chaveNormalizada = chave.strip();
        if (chaveNormalizada.length() > TAMANHO_MAXIMO_CHAVE) {
            throw ApiException.requisicaoInvalida("Idempotency-Key pode ter no máximo 100 caracteres.");
        }
        String hash = hash(requisicao);

        Optional<Resposta<T>> anterior = buscar(usuarioId, chaveNormalizada, hash, tipo);
        if (anterior.isPresent()) {
            return anterior.get();
        }
        try {
            return transacao.execute(status -> {
                Resposta<T> resposta = acao.get();
                repository.saveAndFlush(new RegistroIdempotencia(usuarioId, chaveNormalizada, hash, resposta.status(),
                        json.writeValueAsString(resposta.corpo()), clock.instant()));
                return resposta;
            });
        } catch (DataIntegrityViolationException corrida) {
            log.info("Idempotency-Key repetida em paralelo (usuário {}): devolvendo a resposta já gravada", usuarioId);
            return buscar(usuarioId, chaveNormalizada, hash, tipo).orElseThrow(() -> corrida);
        }
    }

    private <T> Optional<Resposta<T>> buscar(Long usuarioId, String chave, String hash, Class<T> tipo) {
        Instant limite = clock.instant().minus(validade);
        return repository.findByUsuarioIdAndChaveAndCriadoEmAfter(usuarioId, chave, limite).map(r -> {
            if (!r.getHashRequisicao().equals(hash)) {
                throw ApiException.regra("Esta Idempotency-Key já foi usada com outro conteúdo.");
            }
            return new Resposta<>(r.getStatusHttp(), json.readValue(r.getCorpoResposta(), tipo), true);
        });
    }

    private String hash(Object requisicao) {
        try {
            byte[] bytes = json.writeValueAsString(requisicao).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Apaga as chaves vencidas (24 h por padrão) a cada hora. */
    @Scheduled(cron = "0 15 * * * *")
    public void limparVencidas() {
        Integer apagadas = transacao.execute(s -> repository.apagarAnterioresA(clock.instant().minus(validade)));
        if (apagadas != null && apagadas > 0) {
            log.info("{} chaves de idempotência vencidas removidas", apagadas);
        }
    }
}
