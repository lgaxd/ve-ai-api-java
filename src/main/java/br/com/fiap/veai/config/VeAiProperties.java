package br.com.fiap.veai.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurações próprias da aplicação (prefixo {@code veai}).
 */
@ConfigurationProperties(prefix = "veai")
public record VeAiProperties(Jwt jwt, Ia ia, Rag rag, RateLimit rateLimit, Relato relato, Cors cors) {

    /** Assinatura e validade do token. O segredo precisa ter no mínimo 32 bytes (HS256). */
    public record Jwt(String segredo, Duration validade, String emissor) {}

    /** provedor = openai | falso. */
    public record Ia(String provedor, String modelo, double confiancaMinima, long tamanhoMaximoBytes) {
        public boolean openai() {
            return "openai".equalsIgnoreCase(provedor);
        }
    }

    /** Serviço RAG do Pedro. URL vazia = desligado. */
    public record Rag(String url, Duration timeout) {
        public boolean habilitado() {
            return url != null && !url.isBlank();
        }
    }

    public record RateLimit(int descricoesPorMinuto) {}

    public record Relato(double raioDeduplicacaoMetros, Duration validadeIdempotencia) {}

    public record Cors(List<String> origens) {}
}
