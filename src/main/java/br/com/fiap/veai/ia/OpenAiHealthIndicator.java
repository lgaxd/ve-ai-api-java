package br.com.fiap.veai.ia;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import br.com.fiap.veai.config.VeAiProperties;

/**
 * Health check da OpenAI sem gastar tokens: consulta {@code GET /v1/models/{modelo}} (só confere chave e
 * alcance). O resultado fica em cache por 60 s. Com o descritor falso, aparece como UNKNOWN.
 */
@Component("openai")
public class OpenAiHealthIndicator implements HealthIndicator {

    private static final Duration CACHE = Duration.ofSeconds(60);

    private final VeAiProperties.Ia config;
    private final String apiKey;
    private final RestClient http;
    private volatile Health ultimo;
    private volatile Instant ultimoEm = Instant.EPOCH;

    public OpenAiHealthIndicator(VeAiProperties properties, RestClient.Builder builder,
                                 @Value("${spring.ai.openai.api-key:}") String apiKey,
                                 @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl) {
        this.config = properties.ia();
        this.apiKey = apiKey;
        this.http = builder.baseUrl(baseUrl.isBlank() ? "https://api.openai.com" : baseUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings.defaults()
                        .withConnectTimeout(Duration.ofSeconds(3)).withReadTimeout(Duration.ofSeconds(3))))
                .build();
    }

    @Override
    public Health health() {
        if (!config.openai()) {
            return Health.unknown().withDetail("provedor", config.provedor()).withDetail("motivo", "descritor falso ativo").build();
        }
        if (apiKey == null || apiKey.isBlank()) {
            return Health.down().withDetail("motivo", "OPENAI_API_KEY ausente").build();
        }
        if (ultimo != null && Instant.now().isBefore(ultimoEm.plus(CACHE))) {
            return ultimo;
        }
        Health h;
        try {
            http.get().uri("/v1/models/{modelo}", config.modelo())
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve().toBodilessEntity();
            h = Health.up().withDetail("modelo", config.modelo()).build();
        } catch (RuntimeException e) {
            h = Health.down().withDetail("modelo", config.modelo()).withDetail("erro", e.getClass().getSimpleName()).build();
        }
        ultimo = h;
        ultimoEm = Instant.now();
        return h;
    }
}
