package br.com.fiap.veai.descricao;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.fiap.veai.config.VeAiProperties;
import br.com.fiap.veai.relato.TipoRelato;

/**
 * No modo automático, a mesma barreira aparece em várias fotos seguidas. Esta memória evita que o app
 * pergunte "Quer avisar a rede de apoio?" a cada foto: a mesma sugestão (mesmo tipo de barreira) só
 * volta depois da janela configurada. No modo manual a pessoa fotografou de propósito, então a
 * sugestão sempre aparece (e também reinicia a janela).
 *
 * <p>Em memória, como o {@link LimitadorRequisicoes}: suficiente para uma instância do App Service.
 */
@Component
public class MemoriaSugestoes {

    private final Map<Chave, Instant> ultimas = new ConcurrentHashMap<>();
    private final Duration janela;
    private final Clock clock;

    public MemoriaSugestoes(VeAiProperties properties, Clock clock) {
        this.janela = properties.continuo().janelaSugestao();
        this.clock = clock;
    }

    /** Diz se a sugestão deve ir para o app e, se sim, registra que foi feita agora. */
    public boolean deveSugerir(Long usuarioId, TipoRelato tipo, boolean automatico) {
        Instant agora = clock.instant();
        AtomicBoolean sugerir = new AtomicBoolean(true);
        ultimas.compute(new Chave(usuarioId, tipo), (chave, ultima) -> {
            if (automatico && ultima != null && ultima.plus(janela).isAfter(agora)) {
                sugerir.set(false);
                return ultima;
            }
            return agora;
        });
        return sugerir.get();
    }

    /** Remove registros vencidos para o mapa não crescer sem limite. */
    @Scheduled(fixedDelay = 300_000)
    void limpar() {
        Instant corte = clock.instant().minus(janela);
        ultimas.values().removeIf(ultima -> ultima.isBefore(corte));
    }

    private record Chave(Long usuarioId, TipoRelato tipo) {}
}
