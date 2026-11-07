package br.com.fiap.veai.usuario;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import br.com.fiap.veai.regiao.Regiao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Papel papel;

    @Column(name = "velocidade_voz", nullable = false)
    private double velocidadeVoz = 1.0;

    @Column(name = "pergunta_livre_ativa", nullable = false)
    private boolean perguntaLivreAtiva;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    /** Tabela GESTOR_REGIAO: regiões atendidas por um gestor. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "gestor_regiao",
            joinColumns = @JoinColumn(name = "gestor_id"),
            inverseJoinColumns = @JoinColumn(name = "regiao_id"))
    private Set<Regiao> regioes = new HashSet<>();

    protected Usuario() {}

    public static Usuario novoCidadao(String nome, String email, String senhaHash, Instant agora) {
        Usuario u = new Usuario();
        u.nome = nome;
        u.email = email;
        u.senhaHash = senhaHash;
        u.papel = Papel.CIDADAO;
        u.criadoEm = agora;
        return u;
    }

    public void atualizarPreferencias(double velocidadeVoz, boolean perguntaLivreAtiva) {
        this.velocidadeVoz = velocidadeVoz;
        this.perguntaLivreAtiva = perguntaLivreAtiva;
    }

    public boolean atende(Long regiaoId) {
        return regioes.stream().anyMatch(r -> r.getId().equals(regiaoId));
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public Papel getPapel() { return papel; }
    public double getVelocidadeVoz() { return velocidadeVoz; }
    public boolean isPerguntaLivreAtiva() { return perguntaLivreAtiva; }
    public Instant getCriadoEm() { return criadoEm; }
    public Set<Regiao> getRegioes() { return regioes; }
}
