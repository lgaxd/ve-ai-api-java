package br.com.fiap.veai.notificacao;

import java.time.Instant;

import br.com.fiap.veai.relato.Relato;
import br.com.fiap.veai.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Mensagem curta, gravada pelo consumidor RabbitMQ e lida em voz alta pelo app. */
@Entity
@Table(name = "notificacao")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relato_id")
    private Relato relato;

    @Column(nullable = false, length = 300)
    private String mensagem;

    @Column(nullable = false)
    private boolean lida;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Notificacao() {}

    public Notificacao(Usuario usuario, Relato relato, String mensagem, Instant agora) {
        this.usuario = usuario;
        this.relato = relato;
        this.mensagem = mensagem;
        this.criadoEm = agora;
    }

    public void marcarComoLida() {
        this.lida = true;
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public Relato getRelato() { return relato; }
    public String getMensagem() { return mensagem; }
    public boolean isLida() { return lida; }
    public Instant getCriadoEm() { return criadoEm; }
}
