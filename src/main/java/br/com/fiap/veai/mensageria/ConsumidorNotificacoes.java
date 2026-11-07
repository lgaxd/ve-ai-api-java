package br.com.fiap.veai.mensageria;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import br.com.fiap.veai.notificacao.NotificacaoService;

/**
 * Consome {@code veai.notificacoes} e grava as notificações. Ack manual:
 * sucesso → ack; falha → até 3 tentativas com espera crescente; ainda falhou → nack sem requeue → DLQ.
 */
@Component
public class ConsumidorNotificacoes {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorNotificacoes.class);
    static final int TENTATIVAS = 3;

    private final NotificacaoService notificacoes;

    public ConsumidorNotificacoes(NotificacaoService notificacoes) {
        this.notificacoes = notificacoes;
    }

    @RabbitListener(queues = RabbitConfig.FILA_NOTIFICACOES, ackMode = "MANUAL")
    public void receber(EventoRelato evento, Channel canal, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        for (int tentativa = 1; tentativa <= TENTATIVAS; tentativa++) {
            try {
                int criadas = notificacoes.gerar(evento);
                canal.basicAck(tag, false);
                log.info("{} do relato {}: {} notificação(ões) criada(s)", evento.tipo().routingKey(), evento.relatoId(), criadas);
                return;
            } catch (RuntimeException e) {
                log.warn("Tentativa {}/{} falhou para {} do relato {}: {}", tentativa, TENTATIVAS,
                        evento.tipo().routingKey(), evento.relatoId(), e.getMessage());
                esperar(tentativa);
            }
        }
        log.error("Evento {} do relato {} enviado para a DLQ {}", evento.tipo().routingKey(), evento.relatoId(), RabbitConfig.DLQ);
        canal.basicNack(tag, false, false);
    }

    private static void esperar(int tentativa) {
        try {
            Thread.sleep(200L * tentativa);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
