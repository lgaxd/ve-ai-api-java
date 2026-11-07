package br.com.fiap.veai.relato;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Dias em aberto" mudam todo dia: recalcula a prioridade dos relatos não finalizados às 3h
 * (horário de Brasília) e ao subir a aplicação. Equivale ao bloco PL/SQL com cursor do Oracle.
 */
@Component
public class RecalculoPrioridadeJob {

    private static final Logger log = LoggerFactory.getLogger(RecalculoPrioridadeJob.class);

    private final RelatoRepository relatos;
    private final Clock clock;

    public RecalculoPrioridadeJob(RelatoRepository relatos, Clock clock) {
        this.relatos = relatos;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void recalcular() {
        Instant agora = clock.instant();
        var abertos = relatos.findByStatusIn(StatusRelato.NAO_FINAIS);
        abertos.forEach(r -> r.recalcularPrioridade(agora));
        log.info("Prioridade recalculada para {} relatos não finalizados", abertos.size());
    }
}
