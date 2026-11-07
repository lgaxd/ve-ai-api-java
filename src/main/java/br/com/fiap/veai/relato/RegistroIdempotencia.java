package br.com.fiap.veai.relato;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Resposta guardada de um {@code POST /relatos} com {@code Idempotency-Key}: repetir a mesma chave
 * devolve a mesma resposta, sem criar outro relato nem outro apoio.
 */
@Entity
@Table(name = "idempotencia", uniqueConstraints = @UniqueConstraint(name = "uk_idempotencia_usuario_chave",
        columnNames = {"usuario_id", "chave"}))
public class RegistroIdempotencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 100)
    private String chave;

    /** SHA-256 do corpo da requisição: a mesma chave com outro conteúdo é recusada. */
    @Column(name = "hash_requisicao", nullable = false, length = 64)
    private String hashRequisicao;

    @Column(name = "status_http", nullable = false)
    private int statusHttp;

    /** Texto longo: TEXT no PostgreSQL, NVARCHAR(MAX) no SQL Server. */
    @JdbcTypeCode(SqlTypes.LONG32NVARCHAR)
    @Column(name = "corpo_resposta", nullable = false)
    private String corpoResposta;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected RegistroIdempotencia() {}

    public RegistroIdempotencia(Long usuarioId, String chave, String hashRequisicao, int statusHttp,
                                String corpoResposta, Instant agora) {
        this.usuarioId = usuarioId;
        this.chave = chave;
        this.hashRequisicao = hashRequisicao;
        this.statusHttp = statusHttp;
        this.corpoResposta = corpoResposta;
        this.criadoEm = agora;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getChave() { return chave; }
    public String getHashRequisicao() { return hashRequisicao; }
    public int getStatusHttp() { return statusHttp; }
    public String getCorpoResposta() { return corpoResposta; }
    public Instant getCriadoEm() { return criadoEm; }
}
