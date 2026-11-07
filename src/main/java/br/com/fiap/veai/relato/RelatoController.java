package br.com.fiap.veai.relato;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.veai.auth.UsuarioLogado;
import br.com.fiap.veai.comum.PaginaResposta;
import br.com.fiap.veai.relato.IdempotenciaService.Resposta;
import br.com.fiap.veai.relato.RelatoDtos.RegistroRelatoResposta;
import br.com.fiap.veai.relato.RelatoDtos.RelatoEdicaoRequest;
import br.com.fiap.veai.relato.RelatoDtos.RelatoRequest;
import br.com.fiap.veai.relato.RelatoDtos.RelatoResposta;
import br.com.fiap.veai.relato.RelatoDtos.ResultadoRegistro;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/relatos")
@PreAuthorize("hasRole('CIDADAO')")
@Tag(name = "Relatos", description = "Barreiras de acessibilidade relatadas pelo cidadão")
public class RelatoController {

    public static final String HEADER_IDEMPOTENCIA = "Idempotency-Key";
    public static final String HEADER_REPETIDA = "Idempotent-Replayed";

    private final RelatoService service;
    private final IdempotenciaService idempotencia;

    public RelatoController(RelatoService service, IdempotenciaService idempotencia) {
        this.service = service;
        this.idempotencia = idempotencia;
    }

    @PostMapping
    @Operation(summary = "Registra um relato ou +1 apoio (regra dos 30 m)",
            description = "Se já existe um relato aberto do mesmo tipo a até 30 m, registra um apoio nele. "
                    + "Envie Idempotency-Key para que um toque duplo ou uma conexão ruim não dupliquem o envio.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Relato criado (resultado = CRIADO)"),
                    @ApiResponse(responseCode = "200", description = "APOIO_REGISTRADO ou JA_REGISTRADO")})
    public ResponseEntity<RegistroRelatoResposta> registrar(
            UsuarioLogado logado,
            @Parameter(description = "Chave única por envio (ex.: UUID gerado pelo app)")
            @RequestHeader(value = HEADER_IDEMPOTENCIA, required = false) String chave,
            @Valid @RequestBody RelatoRequest req) {
        Resposta<RegistroRelatoResposta> r = idempotencia.executar(logado.id(), chave, req, RegistroRelatoResposta.class,
                () -> {
                    RegistroRelatoResposta corpo = service.registrar(logado.id(), req);
                    int status = corpo.resultado() == ResultadoRegistro.CRIADO ? 201 : 200;
                    return new Resposta<>(status, corpo, false);
                });
        var builder = ResponseEntity.status(r.status());
        if (r.repetida()) {
            builder.header(HEADER_REPETIDA, "true");
        }
        return builder.body(r.corpo());
    }

    @GetMapping("/meus")
    @Operation(summary = "Relatos criados por mim, do mais recente para o mais antigo")
    public PaginaResposta<RelatoResposta> meus(UsuarioLogado logado,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return service.meus(logado.id(), page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe do relato (para o autor e para quem apoiou)")
    public RelatoResposta buscar(UsuarioLogado logado, @PathVariable Long id) {
        return service.buscar(logado.id(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita tipo, gravidade, texto e endereço (só enquanto ABERTO; a localização não muda)")
    public RelatoResposta editar(UsuarioLogado logado, @PathVariable Long id, @Valid @RequestBody RelatoEdicaoRequest req) {
        return service.editar(logado.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui o relato (só enquanto ABERTO e sem apoios de outras pessoas)",
            responses = @ApiResponse(responseCode = "409", description = "Outras pessoas já confirmaram o relato"))
    public void excluir(UsuarioLogado logado, @PathVariable Long id) {
        service.excluir(logado.id(), id);
    }
}
