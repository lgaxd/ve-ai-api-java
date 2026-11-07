package br.com.fiap.veai.relato;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.veai.auth.UsuarioLogado;
import br.com.fiap.veai.comum.PaginaResposta;
import br.com.fiap.veai.relato.RelatoDtos.AtualizacaoResposta;
import br.com.fiap.veai.relato.RelatoDtos.RelatoResposta;
import br.com.fiap.veai.relato.RelatoDtos.StatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/gestor/relatos")
@PreAuthorize("hasRole('GESTOR')")
@Tag(name = "Gestor", description = "Fila da rede de apoio: só as regiões atendidas pelo gestor")
public class GestorRelatoController {

    private final GestorRelatoService service;

    public GestorRelatoController(GestorRelatoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Fila de relatos das minhas regiões, por prioridade",
            responses = @ApiResponse(responseCode = "403", description = "regiaoId de uma região que o gestor não atende"))
    public PaginaResposta<RelatoResposta> fila(UsuarioLogado logado,
                                               @RequestParam(required = false) StatusRelato status,
                                               @RequestParam(required = false) Long regiaoId,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return service.fila(logado.id(), status, regiaoId, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe de um relato da minha região")
    public RelatoResposta buscar(UsuarioLogado logado, @PathVariable Long id) {
        return service.buscar(logado.id(), id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Muda o status: ABERTO → EM_TRIAGEM → ENCAMINHADO (protocolo) → RESOLVIDO, ou DESCARTADO (motivo)",
            responses = {
                    @ApiResponse(responseCode = "403", description = "Relato de uma região que o gestor não atende"),
                    @ApiResponse(responseCode = "422", description = "Transição inválida, protocolo ou motivo ausente")})
    public RelatoResposta atualizarStatus(UsuarioLogado logado, @PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return service.atualizarStatus(logado.id(), id, req);
    }

    @GetMapping("/{id}/historico")
    @Operation(summary = "Histórico de mudanças de status do relato")
    public List<AtualizacaoResposta> historico(UsuarioLogado logado, @PathVariable Long id) {
        return service.historico(logado.id(), id);
    }
}
