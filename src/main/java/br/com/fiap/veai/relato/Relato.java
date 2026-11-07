package br.com.fiap.veai.relato;

import java.time.Instant;

import br.com.fiap.veai.comum.ApiException;
import br.com.fiap.veai.descricao.Descricao;
import br.com.fiap.veai.regiao.Regiao;
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

/** Barreira de acessibilidade relatada por um cidadão. */
@Entity
@Table(name = "relato")
public class Relato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cidadao_id", nullable = false)
    private Usuario cidadao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "descricao_id")
    private Descricao descricao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "regiao_id", nullable = false)
    private Regiao regiao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoRelato tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Gravidade gravidade;

    @Column(nullable = false, length = 1000)
    private String texto;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(length = 200)
    private String endereco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatusRelato status;

    @Column(name = "protocolo_orgao", length = 40)
    private String protocoloOrgao;

    @Column(name = "motivo_descarte", length = 300)
    private String motivoDescarte;

    @Column(name = "total_apoios", nullable = false)
    private int totalApoios;

    @Column(nullable = false)
    private int prioridade;

    @Column(name = "norma_referencia", length = 200)
    private String normaReferencia;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Relato() {}

    public static Relato novo(Usuario cidadao, Descricao descricao, Regiao regiao, TipoRelato tipo, Gravidade gravidade,
                              String texto, double latitude, double longitude, String endereco, Instant agora) {
        Relato r = new Relato();
        r.cidadao = cidadao;
        r.descricao = descricao;
        r.regiao = regiao;
        r.tipo = tipo;
        r.gravidade = gravidade;
        r.texto = texto;
        r.latitude = latitude;
        r.longitude = longitude;
        r.endereco = endereco;
        r.status = StatusRelato.ABERTO;
        r.criadoEm = agora;
        r.atualizadoEm = agora;
        r.recalcularPrioridade(agora);
        return r;
    }

    public void registrarApoio(Instant agora) {
        totalApoios++;
        atualizadoEm = agora;
        recalcularPrioridade(agora);
    }

    /** Edição pelo autor: só enquanto ABERTO e sem mudar as coordenadas (quebraria a deduplicação). */
    public void editar(TipoRelato tipo, Gravidade gravidade, String texto, String endereco, Instant agora) {
        if (status != StatusRelato.ABERTO) {
            throw ApiException.regra("Só é possível editar um relato que ainda está aberto.");
        }
        this.tipo = tipo;
        this.gravidade = gravidade;
        this.texto = texto;
        this.endereco = endereco;
        this.atualizadoEm = agora;
        recalcularPrioridade(agora);
    }

    /** Aplica a máquina de estados e as exigências de protocolo e motivo. */
    public StatusRelato alterarStatus(StatusRelato novo, String protocolo, String motivo, Instant agora) {
        if (!status.podeIrPara(novo)) {
            throw ApiException.regra("Não é possível mudar o relato de " + status + " para " + novo + ".");
        }
        if (novo == StatusRelato.ENCAMINHADO && (protocolo == null || protocolo.isBlank())) {
            throw ApiException.regra("Informe o protocolo do órgão para encaminhar o relato.");
        }
        if (novo == StatusRelato.DESCARTADO && (motivo == null || motivo.isBlank())) {
            throw ApiException.regra("Informe o motivo do descarte.");
        }
        StatusRelato anterior = status;
        status = novo;
        if (novo == StatusRelato.ENCAMINHADO) {
            protocoloOrgao = protocolo.strip();
        }
        if (novo == StatusRelato.DESCARTADO) {
            motivoDescarte = motivo.strip();
        }
        atualizadoEm = agora;
        recalcularPrioridade(agora);
        return anterior;
    }

    public void recalcularPrioridade(Instant agora) {
        prioridade = CalculadoraPrioridade.calcular(gravidade, totalApoios, status, criadoEm, agora);
    }

    public void definirNormaReferencia(String norma) {
        this.normaReferencia = norma;
    }

    public boolean ehDoAutor(Long usuarioId) {
        return cidadao.getId().equals(usuarioId);
    }

    public Long getId() { return id; }
    public Usuario getCidadao() { return cidadao; }
    public Descricao getDescricao() { return descricao; }
    public Regiao getRegiao() { return regiao; }
    public TipoRelato getTipo() { return tipo; }
    public Gravidade getGravidade() { return gravidade; }
    public String getTexto() { return texto; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getEndereco() { return endereco; }
    public StatusRelato getStatus() { return status; }
    public String getProtocoloOrgao() { return protocoloOrgao; }
    public String getMotivoDescarte() { return motivoDescarte; }
    public int getTotalApoios() { return totalApoios; }
    public int getPrioridade() { return prioridade; }
    public String getNormaReferencia() { return normaReferencia; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
}
