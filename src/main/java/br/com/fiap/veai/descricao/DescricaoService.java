package br.com.fiap.veai.descricao;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import br.com.fiap.veai.comum.ApiException;
import br.com.fiap.veai.comum.PaginaResposta;
import br.com.fiap.veai.comum.Paginacao;
import br.com.fiap.veai.config.VeAiProperties;
import br.com.fiap.veai.descricao.DescricaoDtos.DescricaoResposta;
import br.com.fiap.veai.descricao.DescricaoDtos.PerguntaResposta;
import br.com.fiap.veai.ia.ContextoAnterior;
import br.com.fiap.veai.ia.DescricaoIA;
import br.com.fiap.veai.ia.DescritorImagem;
import br.com.fiap.veai.ia.ImagemRecebida;
import br.com.fiap.veai.usuario.Usuario;
import br.com.fiap.veai.usuario.UsuarioRepository;

@Service
public class DescricaoService {

    private final DescricaoRepository repository;
    private final UsuarioRepository usuarios;
    private final DescritorImagem descritor;
    private final LimitadorRequisicoes limitador;
    private final MemoriaSugestoes sugestoes;
    private final Clock clock;
    private final double confiancaMinima;
    private final int ladoMaximoFoto;
    private final Duration validadeContexto;
    private final TransactionTemplate transacao;

    public DescricaoService(DescricaoRepository repository, UsuarioRepository usuarios, DescritorImagem descritor,
                            LimitadorRequisicoes limitador, MemoriaSugestoes sugestoes, Clock clock,
                            VeAiProperties properties, PlatformTransactionManager transactionManager) {
        this.transacao = new TransactionTemplate(transactionManager);
        this.repository = repository;
        this.usuarios = usuarios;
        this.descritor = descritor;
        this.limitador = limitador;
        this.sugestoes = sugestoes;
        this.clock = clock;
        this.confiancaMinima = properties.ia().confiancaMinima();
        this.ladoMaximoFoto = properties.ia().ladoMaximoFoto();
        this.validadeContexto = properties.continuo().validadeContexto();
    }

    /**
     * Fluxo principal: valida e reduz a foto, consome o rate limit, chama a IA, grava só o texto e diz se há
     * barreira para sugerir um relato. A chamada à IA fica fora de transação (não segura conexão do banco).
     *
     * <p>Modo automático: o app manda em {@code anteriorId} o id da resposta anterior. A IA compara a cena
     * nova com ela: com novidade, fala só o que mudou; sem novidade, confirma em uma frase curta (o app fala
     * as duas). A mesma sugestão de relato também não se repete a cada foto. Sem {@code anteriorId}
     * (modo manual), a descrição é completa, como antes.
     */
    public DescricaoResposta descrever(Long usuarioId, ModoDescricao modo, MultipartFile arquivo, Long anteriorId) {
        ImagemRecebida imagem = RedutorImagem.reduzir(ValidadorImagem.validar(arquivo), ladoMaximoFoto);
        Descricao anterior = anteriorId == null ? null
                : daPessoa(usuarioId, anteriorId, "Descrição anterior não encontrada.");
        limitador.consumir(usuarioId);
        ContextoAnterior contexto = contexto(anterior, modo, clock.instant(), validadeContexto);

        long inicio = System.nanoTime();
        DescricaoIA ia = descritor.descrever(imagem, modo, contexto).normalizada();
        int latenciaMs = (int) ((System.nanoTime() - inicio) / 1_000_000);
        // Rede de segurança: se antes não havia perigo e agora há, a pessoa precisa ouvir, diga a IA o que disser
        boolean novidade = contexto == null || ia.novidade()
                || (anterior.getPerigos().isEmpty() && !ia.perigos().isEmpty());

        String completa = modo == ModoDescricao.TEXTO && ia.textoLido() != null && !ia.textoLido().isBlank()
                ? ia.textoLido() : ia.descricaoCompleta();
        boolean barreira = ia.barreira().detectada() && ia.barreira().tipo() != null;
        Descricao salva = transacao.execute(status -> {
            Usuario usuario = usuarios.getReferenceById(usuarioId);
            return repository.save(new Descricao(usuario, modo, limitar(ia.resumo(), 500), limitar(completa, 4000),
                    ia.perigos(), ia.objetos(), barreira, barreira ? ia.barreira().tipo() : null, latenciaMs,
                    clock.instant()));
        });

        boolean automatico = contexto != null;
        var sugestao = RegraSugestaoRelato.avaliar(ia.barreira(), confiancaMinima)
                .filter(s -> sugestoes.deveSugerir(usuarioId, s.tipo(), automatico))
                .orElse(null);
        return DescricaoResposta.de(salva, novidade, sugestao);
    }

    /**
     * A descrição anterior só vale como referência no modo AMBIENTE e por pouco tempo: depois da validade,
     * a pessoa pode ter parado ou mudado de lugar, e a API descreve a cena inteira de novo.
     */
    static ContextoAnterior contexto(Descricao anterior, ModoDescricao modo, Instant agora, Duration validadeContexto) {
        if (anterior == null || modo != ModoDescricao.AMBIENTE || anterior.getModo() != ModoDescricao.AMBIENTE) {
            return null;
        }
        Duration idade = Duration.between(anterior.getCriadoEm(), agora);
        if (idade.compareTo(validadeContexto) > 0) {
            return null;
        }
        return new ContextoAnterior(anterior.getDescricaoCompleta(), anterior.getResumo(), anterior.getPerigos(),
                Math.max(0, idade.toSeconds()));
    }

    @Transactional(readOnly = true)
    public PaginaResposta<DescricaoResposta> listar(Long usuarioId, Integer page, Integer size) {
        var pagina = repository.findByUsuarioId(usuarioId, Paginacao.de(page, size, Sort.by(Sort.Direction.DESC, "criadoEm", "id")));
        return PaginaResposta.de(pagina, d -> DescricaoResposta.de(d));
    }

    @Transactional(readOnly = true)
    public DescricaoResposta buscar(Long usuarioId, Long id) {
        return DescricaoResposta.de(daPessoa(usuarioId, id));
    }

    @Transactional
    public DescricaoResposta avaliar(Long usuarioId, Long id, boolean util) {
        Descricao d = daPessoa(usuarioId, id);
        d.avaliar(util);
        return DescricaoResposta.de(d);
    }

    /** Relatos criados a partir desta descrição continuam existindo (descricao_id vira NULL). */
    @Transactional
    public void excluir(Long usuarioId, Long id) {
        repository.delete(daPessoa(usuarioId, id));
    }

    /** Pergunta livre por voz sobre a última foto: exige a preferência ativa e nada é gravado. */
    public PerguntaResposta perguntar(Long usuarioId, Long id, String pergunta, MultipartFile arquivo) {
        if (pergunta == null || pergunta.isBlank() || pergunta.length() > 500) {
            throw ApiException.requisicaoInvalida("Faça uma pergunta de até 500 caracteres.");
        }
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
        if (!usuario.isPerguntaLivreAtiva()) {
            throw ApiException.proibido("Ative a pergunta livre por voz nas preferências.");
        }
        daPessoa(usuarioId, id);
        ImagemRecebida imagem = RedutorImagem.reduzir(ValidadorImagem.validar(arquivo), ladoMaximoFoto);
        limitador.consumir(usuarioId);
        return new PerguntaResposta(descritor.responder(imagem, pergunta.strip()));
    }

    private Descricao daPessoa(Long usuarioId, Long id) {
        return daPessoa(usuarioId, id, "Descrição não encontrada.");
    }

    private Descricao daPessoa(Long usuarioId, Long id, String mensagem) {
        return repository.findByIdAndUsuarioId(id, usuarioId).orElseThrow(() -> ApiException.naoEncontrado(mensagem));
    }

    private static String limitar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
