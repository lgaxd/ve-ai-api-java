package br.com.fiap.veai.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurações próprias da aplicação (prefixo {@code veai}).
 */
@ConfigurationProperties(prefix = "veai")
public record VeAiProperties(Jwt jwt, Ia ia, Continuo continuo, Rag rag, RateLimit rateLimit, Relato relato, Cors cors) {

    /** Assinatura e validade do token. O segredo precisa ter no mínimo 32 bytes (HS256). */
    public record Jwt(String segredo, Duration validade, String emissor) {}

    /** provedor = openai | falso. A foto é reduzida para ladoMaximoFoto pixels antes de ir para o modelo. */
    public record Ia(String provedor, String modelo, double confiancaMinima, long tamanhoMaximoBytes, int ladoMaximoFoto) {
        public boolean openai() {
            return "openai".equalsIgnoreCase(provedor);
        }
    }

    /**
     * Modo automático (fotos a cada poucos segundos enquanto a pessoa anda).
     * validadeContexto: até quando a descrição anterior serve de referência para falar só o que mudou.
     * janelaSugestao: tempo em que a mesma sugestão de relato não é repetida.
     */
    public record Continuo(Duration validadeContexto, Duration janelaSugestao) {}

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
