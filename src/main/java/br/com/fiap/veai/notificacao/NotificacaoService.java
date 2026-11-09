package br.com.fiap.veai.notificacao;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.veai.comum.ApiException;
import br.com.fiap.veai.comum.PaginaResposta;
import br.com.fiap.veai.comum.Paginacao;
import br.com.fiap.veai.mensageria.EventoRelato;
import br.com.fiap.veai.relato.ApoioRelatoRepository;
import br.com.fiap.veai.relato.Relato;
import br.com.fiap.veai.relato.RelatoRepository;
import br.com.fiap.veai.usuario.UsuarioRepository;

@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    private final NotificacaoRepository repository;
    private final RelatoRepository relatos;
    private final ApoioRelatoRepository apoios;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public NotificacaoService(NotificacaoRepository repository, RelatoRepository relatos, ApoioRelatoRepository apoios,
                              UsuarioRepository usuarios, Clock clock) {
        this.repository = repository;
        this.relatos = relatos;
        this.apoios = apoios;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaResposta<NotificacaoResposta> listar(Long usuarioId, Boolean lida, Integer page, Integer size) {
        var pageable = Paginacao.de(page, size, Sort.by(Sort.Direction.DESC, "criadoEm").and(Sort.by(Sort.Direction.DESC, "id")));
        var pagina = lida == null ? repository.findByUsuarioId(usuarioId, pageable)
                : repository.findByUsuarioIdAndLida(usuarioId, lida, pageable);
        return PaginaResposta.de(pagina, NotificacaoResposta::de);
    }

    @Transactional
    public NotificacaoResposta marcarComoLida(Long usuarioId, Long id) {
        Notificacao n = repository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Notificação não encontrada."));
        n.marcarComoLida();
        return NotificacaoResposta.de(n);
    }

    /**
     * Chamado pelo consumidor RabbitMQ. Destinatários:
     * relato.criado → gestores da região · relato.apoiado → autor · relato.status → autor e apoiadores.
     *
     * <p>O relato é travado (SELECT ... FOR UPDATE) antes de inserir as notificações, na mesma ordem da
     * exclusão (trava o relato e apaga as notificações em cascata). Sem isso, um DELETE do autor no meio
     * do consumo dava ORA-02291 ou, com mais de um destinatário, deadlock (ORA-00060) e HTTP 500 na exclusão.
     * Agora um espera o outro: se a exclusão vence, o relato some e o evento é ignorado.
     */
    @Transactional
    public int gerar(EventoRelato evento) {
        Relato relato = relatos.buscarParaAtualizar(evento.relatoId()).orElse(null);
        if (relato == null) {
            log.info("Relato {} não existe mais: evento {} ignorado", evento.relatoId(), evento.tipo());
            return 0;
        }
        Instant agora = clock.instant();
        Long autorId = relato.getCidadao().getId();
        int criadas = 0;
        switch (evento.tipo()) {
            case CRIADO -> {
                for (Long gestorId : usuarios.idsDosGestoresDaRegiao(relato.getRegiao().getId())) {
                    criadas += salvar(gestorId, relato, MensagensNotificacao.novoRelato(relato), agora);
                }
            }
            case APOIADO -> criadas += salvar(autorId, relato, MensagensNotificacao.apoio(relato), agora);
            case STATUS -> {
                criadas += salvar(autorId, relato, MensagensNotificacao.status(relato, evento.statusNovo(), true), agora);
                Set<Long> apoiadores = new LinkedHashSet<>(apoios.idsDosApoiadores(relato.getId()));
                apoiadores.remove(autorId);
                for (Long apoiadorId : apoiadores) {
                    criadas += salvar(apoiadorId, relato, MensagensNotificacao.status(relato, evento.statusNovo(), false), agora);
                }
            }
        }
        return criadas;
    }

    private int salvar(Long usuarioId, Relato relato, String mensagem, Instant agora) {
        String texto = mensagem.length() <= 300 ? mensagem : mensagem.substring(0, 297) + "...";
        repository.save(new Notificacao(usuarios.getReferenceById(usuarioId), relato, texto, agora));
        return 1;
    }
}
