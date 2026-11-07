package br.com.fiap.veai.notificacao;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.veai.auth.UsuarioLogado;
import br.com.fiap.veai.comum.PaginaResposta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/notificacoes")
@Tag(name = "Notificações", description = "Avisos gerados pelo consumidor RabbitMQ e lidos em voz alta pelo app")
public class NotificacaoController {

    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Minhas notificações, da mais recente para a mais antiga")
    public PaginaResposta<NotificacaoResposta> listar(UsuarioLogado logado,
                                                      @Parameter(description = "false = só as não lidas")
                                                      @RequestParam(required = false) Boolean lida,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size) {
        return service.listar(logado.id(), lida, page, size);
    }

    @PatchMapping("/{id}/lida")
    @Operation(summary = "Marca a notificação como lida")
    public NotificacaoResposta marcarComoLida(UsuarioLogado logado, @PathVariable Long id) {
        return service.marcarComoLida(logado.id(), id);
    }
}
