package br.com.fiap.veai.descricao;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.fiap.veai.comum.LimiteExcedidoException;
import br.com.fiap.veai.config.VeAiProperties;

/**
 * Rate limit de janela fixa por usuário (padrão: 20 chamadas à IA por minuto).
 * Controla o custo da OpenAI. Em memória: suficiente para uma instância do App Service.
 */
@Component
public class LimitadorRequisicoes {

    private static final long JANELA_MS = 60_000;

    private final Map<Long, Janela> janelas = new ConcurrentHashMap<>();
    private final int limite;
    private final Clock clock;

    public LimitadorRequisicoes(VeAiProperties properties, Clock clock) {
        this.limite = properties.rateLimit().descricoesPorMinuto();
        this.clock = clock;
    }

    /** Conta mais uma chamada do usuário ou lança {@link LimiteExcedidoException} (HTTP 429). */
    public void consumir(Long usuarioId) {
        long agora = clock.millis();
        long inicioJanela = agora - (agora % JANELA_MS);
        Janela janela = janelas.compute(usuarioId, (id, atual) ->
                atual == null || atual.inicio() != inicioJanela ? new Janela(inicioJanela, 1) : atual.mais1());
        if (janela.contador() > limite) {
            long segundos = Math.max(1, (inicioJanela + JANELA_MS - agora + 999) / 1000);
            throw new LimiteExcedidoException(segundos);
        }
    }

    /** Remove janelas antigas para o mapa não crescer sem limite. */
    @Scheduled(fixedDelay = 300_000)
    void limpar() {
        long corte = clock.millis() - JANELA_MS;
        janelas.values().removeIf(j -> j.inicio() < corte);
    }

    private record Janela(long inicio, int contador) {
        Janela mais1() {
            return new Janela(inicio, contador + 1);
        }
    }
}
