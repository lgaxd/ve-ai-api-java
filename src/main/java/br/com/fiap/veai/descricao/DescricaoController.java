package br.com.fiap.veai.descricao;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.fiap.veai.auth.UsuarioLogado;
import br.com.fiap.veai.comum.PaginaResposta;
import br.com.fiap.veai.descricao.DescricaoDtos.AvaliacaoRequest;
import br.com.fiap.veai.descricao.DescricaoDtos.DescricaoResposta;
import br.com.fiap.veai.descricao.DescricaoDtos.PerguntaResposta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/descricoes")
@PreAuthorize("hasRole('CIDADAO')")
@Tag(name = "Descrições", description = "Descrição do ambiente por IA e histórico (só texto: a foto nunca é guardada)")
public class DescricaoController {

    private final DescricaoService service;

    public DescricaoController(DescricaoService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Descreve a foto (perigos primeiro) e sugere relato se houver barreira",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Descrição criada"),
                    @ApiResponse(responseCode = "413", description = "Foto acima de 5 MB"),
                    @ApiResponse(responseCode = "415", description = "Formato diferente de JPEG, PNG ou WebP"),
                    @ApiResponse(responseCode = "429", description = "Limite de fotos por minuto (header Retry-After)")})
    public DescricaoResposta descrever(UsuarioLogado logado,
                                       @RequestPart("imagem") MultipartFile imagem,
                                       @RequestParam(defaultValue = "AMBIENTE") ModoDescricao modo) {
        return service.descrever(logado.id(), modo, imagem);
    }

    @GetMapping
    @Operation(summary = "Histórico de descrições, da mais recente para a mais antiga")
    public PaginaResposta<DescricaoResposta> listar(UsuarioLogado logado,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return service.listar(logado.id(), page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe de uma descrição")
    public DescricaoResposta buscar(UsuarioLogado logado, @PathVariable Long id) {
        return service.buscar(logado.id(), id);
    }

    @PatchMapping("/{id}/avaliacao")
    @Operation(summary = "Marca a descrição como útil ou não útil")
    public DescricaoResposta avaliar(UsuarioLogado logado, @PathVariable Long id, @Valid @RequestBody AvaliacaoRequest req) {
        return service.avaliar(logado.id(), id, req.util());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui a descrição do histórico")
    public void excluir(UsuarioLogado logado, @PathVariable Long id) {
        service.excluir(logado.id(), id);
    }

    @PostMapping(value = "/{id}/pergunta", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Pergunta livre por voz sobre a foto (o app reenvia a imagem; nada é gravado)",
            description = "Exige perguntaLivreAtiva = true nas preferências. Conta no mesmo limite por minuto.")
    public PerguntaResposta perguntar(UsuarioLogado logado, @PathVariable Long id,
                                      @RequestPart("imagem") MultipartFile imagem,
                                      @RequestParam String pergunta) {
        return service.perguntar(logado.id(), id, pergunta, imagem);
    }
}
