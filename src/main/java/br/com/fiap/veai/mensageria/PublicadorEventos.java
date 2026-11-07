package br.com.fiap.veai.mensageria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica no RabbitMQ só DEPOIS do commit: nunca notifica algo que foi desfeito.
 * Se o broker estiver fora, a operação do usuário já foi gravada; a falha fica no log.
 */
@Component
public class PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

    private final RabbitTemplate rabbit;

    public PublicadorEventos(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(EventoRelato evento) {
        try {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, evento.tipo().routingKey(), evento);
            log.debug("Evento publicado: {} relato {}", evento.tipo().routingKey(), evento.relatoId());
        } catch (AmqpException e) {
            log.error("Não foi possível publicar {} do relato {}: {}", evento.tipo().routingKey(), evento.relatoId(),
                    e.getMessage());
        }
    }
}
