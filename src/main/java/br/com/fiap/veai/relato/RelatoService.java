package br.com.fiap.veai.relato;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.veai.comum.ApiException;
import br.com.fiap.veai.comum.Geo;
import br.com.fiap.veai.comum.PaginaResposta;
import br.com.fiap.veai.comum.Paginacao;
import br.com.fiap.veai.config.VeAiProperties;
import br.com.fiap.veai.descricao.Descricao;
import br.com.fiap.veai.descricao.DescricaoRepository;
import br.com.fiap.veai.mensageria.EventoRelato;
import br.com.fiap.veai.regiao.Regiao;
import br.com.fiap.veai.regiao.RegiaoService;
import br.com.fiap.veai.relato.RelatoDtos.RegistroRelatoResposta;
import br.com.fiap.veai.relato.RelatoDtos.RelatoEdicaoRequest;
import br.com.fiap.veai.relato.RelatoDtos.RelatoRequest;
import br.com.fiap.veai.relato.RelatoDtos.RelatoResposta;
import br.com.fiap.veai.relato.RelatoDtos.ResultadoRegistro;
import br.com.fiap.veai.usuario.Usuario;
import br.com.fiap.veai.usuario.UsuarioRepository;

/** Relatos do ponto de vista do cidadão: registrar (com deduplicação), acompanhar, editar e excluir. */
@Service
public class RelatoService {

    private final RelatoRepository relatos;
    private final ApoioRelatoRepository apoios;
    private final DescricaoRepository descricoes;
    private final UsuarioRepository usuarios;
    private final RegiaoService regioes;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final double raioMetros;

    public RelatoService(RelatoRepository relatos, ApoioRelatoRepository apoios, DescricaoRepository descricoes,
                         UsuarioRepository usuarios, RegiaoService regioes, ApplicationEventPublisher eventos,
                         Clock clock, VeAiProperties properties) {
        this.relatos = relatos;
        this.apoios = apoios;
        this.descricoes = descricoes;
        this.usuarios = usuarios;
        this.regioes = regioes;
        this.eventos = eventos;
        this.clock = clock;
        this.raioMetros = properties.relato().raioDeduplicacaoMetros();
    }

    /**
     * Regra dos 30 m: se já existe um relato não finalizado do mesmo tipo a até 30 m, registra +1 apoio
     * em vez de criar outro. O autor ou quem já apoiou recebe JA_REGISTRADO, sem duplicar nada.
     */
    @Transactional
    public RegistroRelatoResposta registrar(Long usuarioId, RelatoRequest req) {
        Instant agora = clock.instant();
        Usuario cidadao = usuarios.getReferenceById(usuarioId);

        Optional<Relato> proximo = buscarProximo(req.tipo(), req.latitude(), req.longitude());
        if (proximo.isPresent()) {
            Relato existente = proximo.get();
            if (existente.ehDoAutor(usuarioId) || apoios.existsByRelatoIdAndUsuarioId(existente.getId(), usuarioId)) {
                return resposta(ResultadoRegistro.JA_REGISTRADO, existente);
            }
            apoios.save(new ApoioRelato(existente, cidadao, agora));
            existente.registrarApoio(agora);
            eventos.publishEvent(EventoRelato.apoiado(existente.getId(), usuarioId, agora));
            return resposta(ResultadoRegistro.APOIO_REGISTRADO, existente);
        }

        Regiao regiao = req.regiaoId() != null ? regioes.buscar(req.regiaoId())
                : regioes.maisProxima(req.latitude(), req.longitude());
        Descricao descricao = req.descricaoId() == null ? null : descricoes.findByIdAndUsuarioId(req.descricaoId(), usuarioId)
                .orElseThrow(() -> ApiException.naoEncontrado("Descrição de origem não encontrada."));
        Relato novo = relatos.save(Relato.novo(cidadao, descricao, regiao, req.tipo(), req.gravidade(), req.texto().strip(),
                req.latitude(), req.longitude(), vazioParaNulo(req.endereco()), agora));
        eventos.publishEvent(EventoRelato.criado(novo.getId(), usuarioId, agora));
        return resposta(ResultadoRegistro.CRIADO, novo);
    }

    /** Pré-filtro por "caixa" no banco e distância exata (Haversine) em Java; pega o mais próximo. */
    Optional<Relato> buscarProximo(TipoRelato tipo, double lat, double lon) {
        double dLat = Geo.margemLatitude(raioMetros);
        double dLon = Geo.margemLongitude(raioMetros, lat);
        return relatos.candidatosProximos(tipo, StatusRelato.NAO_FINAIS, lat - dLat, lat + dLat, lon - dLon, lon + dLon)
                .stream()
                .filter(r -> Geo.distanciaMetros(lat, lon, r.getLatitude(), r.getLongitude()) <= raioMetros)
                .min(Comparator.comparingDouble(r -> Geo.distanciaMetros(lat, lon, r.getLatitude(), r.getLongitude())));
    }

    @Transactional(readOnly = true)
    public PaginaResposta<RelatoResposta> meus(Long usuarioId, Integer page, Integer size) {
        var pagina = relatos.findByCidadaoId(usuarioId, Paginacao.de(page, size, Sort.by(Sort.Direction.DESC, "criadoEm", "id")));
        return PaginaResposta.de(pagina, RelatoResposta::de);
    }

    /** O autor e quem apoiou podem acompanhar o relato. */
    @Transactional(readOnly = true)
    public RelatoResposta buscar(Long usuarioId, Long id) {
        Relato r = relatos.buscarCompleto(id).orElseThrow(RelatoService::naoEncontrado);
        if (!r.ehDoAutor(usuarioId) && !apoios.existsByRelatoIdAndUsuarioId(id, usuarioId)) {
            throw naoEncontrado();
        }
        return RelatoResposta.de(r);
    }

    @Transactional
    public RelatoResposta editar(Long usuarioId, Long id, RelatoEdicaoRequest req) {
        Relato r = doAutor(usuarioId, id);
        r.editar(req.tipo(), req.gravidade(), req.texto().strip(), vazioParaNulo(req.endereco()), clock.instant());
        return RelatoResposta.de(r);
    }

    /** Só enquanto ABERTO e sem apoios: apagar o relato apagaria a confirmação de outras pessoas. */
    @Transactional
    public void excluir(Long usuarioId, Long id) {
        Relato r = doAutor(usuarioId, id);
        if (r.getStatus() != StatusRelato.ABERTO) {
            throw ApiException.regra("Só é possível excluir um relato que ainda está aberto.");
        }
        if (r.getTotalApoios() > 0) {
            throw ApiException.conflito("Outras pessoas já confirmaram este relato. Ele não pode ser excluído.");
        }
        relatos.delete(r);
    }

    private Relato doAutor(Long usuarioId, Long id) {
        Relato r = relatos.buscarParaAtualizar(id).orElseThrow(RelatoService::naoEncontrado);
        if (!r.ehDoAutor(usuarioId)) {
            throw naoEncontrado();
        }
        return r;
    }

    private static RegistroRelatoResposta resposta(ResultadoRegistro resultado, Relato r) {
        return new RegistroRelatoResposta(resultado, resultado == ResultadoRegistro.APOIO_REGISTRADO, RelatoResposta.de(r));
    }

    private static ApiException naoEncontrado() {
        return ApiException.naoEncontrado("Relato não encontrado.");
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
