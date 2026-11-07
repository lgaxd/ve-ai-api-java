package br.com.fiap.veai.relato;

import java.time.Instant;

import br.com.fiap.veai.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Histórico de negócio: qual gestor mudou o status, de quê para quê e com qual comentário. */
@Entity
@Table(name = "atualizacao_relato")
public class AtualizacaoRelato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relato_id", nullable = false)
    private Relato relato;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gestor_id", nullable = false)
    private Usuario gestor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", nullable = false, length = 12)
    private StatusRelato statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, length = 12)
    private StatusRelato statusNovo;

    @Column(length = 500)
    private String comentario;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected AtualizacaoRelato() {}

    public AtualizacaoRelato(Relato relato, Usuario gestor, StatusRelato anterior, StatusRelato novo,
                             String comentario, Instant agora) {
        this.relato = relato;
        this.gestor = gestor;
        this.statusAnterior = anterior;
        this.statusNovo = novo;
        this.comentario = comentario;
        this.criadoEm = agora;
    }

    public Long getId() { return id; }
    public Relato getRelato() { return relato; }
    public Usuario getGestor() { return gestor; }
    public StatusRelato getStatusAnterior() { return statusAnterior; }
    public StatusRelato getStatusNovo() { return statusNovo; }
    public String getComentario() { return comentario; }
    public Instant getCriadoEm() { return criadoEm; }
}
