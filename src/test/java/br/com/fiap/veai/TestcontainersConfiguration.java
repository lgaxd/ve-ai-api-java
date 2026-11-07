package br.com.fiap.veai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/** Oracle XE e RabbitMQ reais em container, nas mesmas versões do docker-compose.yml. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    OracleContainer oracle() {
        return new OracleContainer(DockerImageName.parse("gvenzl/oracle-xe:21-slim-faststart"))
                .withUsername("veai")
                .withPassword("veai");
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"));
    }
}
