package br.com.fiap.veai.relato;

import java.time.Instant;

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
import jakarta.persistence.UniqueConstraint;

/** "+1": outro cidadão confirmou a mesma barreira. Único por relato e usuário. */
@Entity
@Table(name = "apoio_relato", uniqueConstraints = @UniqueConstraint(name = "uk_apoio_relato_usuario",
        columnNames = {"relato_id", "usuario_id"}))
public class ApoioRelato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relato_id", nullable = false)
    private Relato relato;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected ApoioRelato() {}

    public ApoioRelato(Relato relato, Usuario usuario, Instant agora) {
        this.relato = relato;
        this.usuario = usuario;
        this.criadoEm = agora;
    }

    public Long getId() { return id; }
    public Relato getRelato() { return relato; }
    public Usuario getUsuario() { return usuario; }
    public Instant getCriadoEm() { return criadoEm; }
}
