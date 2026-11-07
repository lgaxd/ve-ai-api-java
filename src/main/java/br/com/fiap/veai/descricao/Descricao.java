package br.com.fiap.veai.descricao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import br.com.fiap.veai.ia.Perigo;
import br.com.fiap.veai.relato.TipoRelato;
import br.com.fiap.veai.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/** Descrição gerada pela IA. Só o texto é guardado: a imagem nunca é armazenada. */
@Entity
@Table(name = "descricao")
public class Descricao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ModoDescricao modo;

    @Column(nullable = false, length = 500)
    private String resumo;

    /** CLOB no Oracle; a API limita a 4000 caracteres (tamanho da coluna no SQL Server). */
    @Column(name = "descricao_completa", nullable = false, length = 4000)
    private String descricaoCompleta;

    /** Texto JSON: CLOB com CHECK IS JSON no Oracle, nvarchar(max) com ISJSON no SQL Server. */
    @Convert(converter = ListaJsonConverter.Perigos.class)
    @Column(nullable = false)
    private List<Perigo> perigos = new ArrayList<>();

    @Convert(converter = ListaJsonConverter.Textos.class)
    @Column(nullable = false)
    private List<String> objetos = new ArrayList<>();

    @Column(name = "barreira_detectada", nullable = false)
    private boolean barreiraDetectada;

    @Enumerated(EnumType.STRING)
    @Column(name = "barreira_tipo", length = 30)
    private TipoRelato barreiraTipo;

    @Column(name = "latencia_ms", nullable = false)
    private int latenciaMs;

    /** null = ainda não avaliada pelo cidadão. */
    private Boolean util;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Descricao() {}

    public Descricao(Usuario usuario, ModoDescricao modo, String resumo, String descricaoCompleta, List<Perigo> perigos,
                     List<String> objetos, boolean barreiraDetectada, TipoRelato barreiraTipo, int latenciaMs,
                     Instant agora) {
        this.usuario = usuario;
        this.modo = modo;
        this.resumo = resumo;
        this.descricaoCompleta = descricaoCompleta;
        this.perigos = new ArrayList<>(perigos);
        this.objetos = new ArrayList<>(objetos);
        this.barreiraDetectada = barreiraDetectada;
        this.barreiraTipo = barreiraTipo;
        this.latenciaMs = latenciaMs;
        this.criadoEm = agora;
    }

    public void avaliar(boolean util) {
        this.util = util;
    }

    public boolean ehDo(Long usuarioId) {
        return usuario.getId().equals(usuarioId);
    }

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public ModoDescricao getModo() { return modo; }
    public String getResumo() { return resumo; }
    public String getDescricaoCompleta() { return descricaoCompleta; }
    public List<Perigo> getPerigos() { return perigos; }
    public List<String> getObjetos() { return objetos; }
    public boolean isBarreiraDetectada() { return barreiraDetectada; }
    public TipoRelato getBarreiraTipo() { return barreiraTipo; }
    public int getLatenciaMs() { return latenciaMs; }
    public Boolean getUtil() { return util; }
    public Instant getCriadoEm() { return criadoEm; }
}
