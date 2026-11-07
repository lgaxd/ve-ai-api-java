package br.com.fiap.veai.mensageria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/**
 * Topologia RabbitMQ (opção A do requisito 5):
 * <pre>
 * veai.relatos (topic) --relato.*--> veai.notificacoes --falha--> veai.relatos.dlx --> veai.notificacoes.dlq
 * </pre>
 * Tudo durável. Mensagens que falham depois das novas tentativas vão para a dead-letter queue.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "veai.relatos";
    public static final String FILA_NOTIFICACOES = "veai.notificacoes";
    public static final String DLX = "veai.relatos.dlx";
    public static final String DLQ = "veai.notificacoes.dlq";

    @Bean
    TopicExchange exchangeRelatos() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue filaNotificacoes() {
        return QueueBuilder.durable(FILA_NOTIFICACOES)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    @Bean
    Queue filaDeadLetter() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding bindingNotificacoes(Queue filaNotificacoes, TopicExchange exchangeRelatos) {
        return BindingBuilder.bind(filaNotificacoes).to(exchangeRelatos).with("relato.*");
    }

    @Bean
    Binding bindingDeadLetter(Queue filaDeadLetter, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(filaDeadLetter).to(deadLetterExchange).with(DLQ);
    }

    /** Mensagens em JSON (legíveis no painel do RabbitMQ), com o mesmo Jackson da API. */
    @Bean
    MessageConverter conversorJson(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
