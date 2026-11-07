package br.com.fiap.veai.mensageria;

import java.time.Instant;

import br.com.fiap.veai.relato.StatusRelato;

/**
 * Evento de domínio publicado depois do commit e enviado ao RabbitMQ (exchange {@code veai.relatos}).
 *
 * @param usuarioId quem causou o evento (autor, apoiador ou gestor)
 */
public record EventoRelato(Tipo tipo, Long relatoId, Long usuarioId, StatusRelato statusAnterior,
                           StatusRelato statusNovo, Instant ocorridoEm) {

    public enum Tipo {
        CRIADO("relato.criado"), APOIADO("relato.apoiado"), STATUS("relato.status");

        private final String routingKey;

        Tipo(String routingKey) {
            this.routingKey = routingKey;
        }

        public String routingKey() {
            return routingKey;
        }
    }

    public static EventoRelato criado(Long relatoId, Long autorId, Instant agora) {
        return new EventoRelato(Tipo.CRIADO, relatoId, autorId, null, StatusRelato.ABERTO, agora);
    }

    public static EventoRelato apoiado(Long relatoId, Long apoiadorId, Instant agora) {
        return new EventoRelato(Tipo.APOIADO, relatoId, apoiadorId, null, null, agora);
    }

    public static EventoRelato status(Long relatoId, Long gestorId, StatusRelato anterior, StatusRelato novo, Instant agora) {
        return new EventoRelato(Tipo.STATUS, relatoId, gestorId, anterior, novo, agora);
    }
}
