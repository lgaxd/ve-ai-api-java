package br.com.fiap.veai.relato;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.veai.comum.ApiException;
import br.com.fiap.veai.comum.PaginaResposta;
import br.com.fiap.veai.comum.Paginacao;
import br.com.fiap.veai.mensageria.EventoRelato;
import br.com.fiap.veai.regiao.Regiao;
import br.com.fiap.veai.relato.RelatoDtos.AtualizacaoResposta;
import br.com.fiap.veai.relato.RelatoDtos.RelatoResposta;
import br.com.fiap.veai.relato.RelatoDtos.StatusRequest;
import br.com.fiap.veai.usuario.Usuario;
import br.com.fiap.veai.usuario.UsuarioRepository;

import org.springframework.context.ApplicationEventPublisher;

/** Relatos do ponto de vista do gestor: fila por prioridade, mudança de status e histórico. */
@Service
public class GestorRelatoService {

    // O id desempata: no Oracle as datas são DATE (precisão de segundos), então empates são comuns
    private static final Sort FILA = Sort.by(Sort.Order.desc("prioridade"), Sort.Order.asc("criadoEm"), Sort.Order.asc("id"));

    private final RelatoRepository relatos;
    private final AtualizacaoRelatoRepository atualizacoes;
    private final UsuarioRepository usuarios;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public GestorRelatoService(RelatoRepository relatos, AtualizacaoRelatoRepository atualizacoes,
                               UsuarioRepository usuarios, ApplicationEventPublisher eventos, Clock clock) {
        this.relatos = relatos;
        this.atualizacoes = atualizacoes;
        this.usuarios = usuarios;
        this.eventos = eventos;
        this.clock = clock;
    }

    /** Fila só com as regiões do gestor, ordenada por prioridade (maior primeiro). */
    @Transactional(readOnly = true)
    public PaginaResposta<RelatoResposta> fila(Long gestorId, StatusRelato status, Long regiaoId, Integer page, Integer size) {
        Usuario gestor = gestor(gestorId);
        Set<Long> regioes;
        if (regiaoId != null) {
            exigirRegiao(gestor, regiaoId);
            regioes = Set.of(regiaoId);
        } else {
            regioes = gestor.getRegioes().stream().map(Regiao::getId).collect(java.util.stream.Collectors.toSet());
        }
        Pageable pageable = Paginacao.de(page, size, FILA);
        if (regioes.isEmpty()) {
            return PaginaResposta.de(Page.<Relato>empty(pageable), RelatoResposta::de);
        }
        Page<Relato> pagina = status == null
                ? relatos.findByRegiaoIdIn(regioes, pageable)
                : relatos.findByRegiaoIdInAndStatus(regioes, status, pageable);
        return PaginaResposta.de(pagina, RelatoResposta::de);
    }

    @Transactional(readOnly = true)
    public RelatoResposta buscar(Long gestorId, Long relatoId) {
        Relato r = relatos.buscarCompleto(relatoId).orElseThrow(GestorRelatoService::naoEncontrado);
        exigirRegiao(gestor(gestorId), r.getRegiao().getId());
        return RelatoResposta.de(r);
    }

    /**
     * Valida a transição (máquina de estados), grava o histórico e publica {@code relato.status}:
     * o consumidor RabbitMQ notifica o autor e os apoiadores.
     */
    @Transactional
    public RelatoResposta atualizarStatus(Long gestorId, Long relatoId, StatusRequest req) {
        Relato r = relatos.buscarParaAtualizar(relatoId).orElseThrow(GestorRelatoService::naoEncontrado);
        Usuario gestor = gestor(gestorId);
        exigirRegiao(gestor, r.getRegiao().getId());

        Instant agora = clock.instant();
        StatusRelato anterior = r.alterarStatus(req.status(), req.protocoloOrgao(), req.motivoDescarte(), agora);
        String comentario = req.comentario() == null || req.comentario().isBlank() ? null : req.comentario().strip();
        atualizacoes.save(new AtualizacaoRelato(r, gestor, anterior, req.status(), comentario, agora));
        eventos.publishEvent(EventoRelato.status(r.getId(), gestorId, anterior, req.status(), agora));
        return RelatoResposta.de(r);
    }

    @Transactional(readOnly = true)
    public List<AtualizacaoResposta> historico(Long gestorId, Long relatoId) {
        Relato r = relatos.buscarCompleto(relatoId).orElseThrow(GestorRelatoService::naoEncontrado);
        exigirRegiao(gestor(gestorId), r.getRegiao().getId());
        return atualizacoes.findByRelatoIdOrderByCriadoEmAscIdAsc(relatoId).stream().map(AtualizacaoResposta::de).toList();
    }

    private Usuario gestor(Long gestorId) {
        return usuarios.buscarComRegioes(gestorId).orElseThrow(() -> ApiException.proibido("Gestor não encontrado."));
    }

    private static void exigirRegiao(Usuario gestor, Long regiaoId) {
        if (!gestor.atende(regiaoId)) {
            throw ApiException.proibido("Este relato é de uma região que você não atende.");
        }
    }

    private static ApiException naoEncontrado() {
        return ApiException.naoEncontrado("Relato não encontrado.");
    }
}
