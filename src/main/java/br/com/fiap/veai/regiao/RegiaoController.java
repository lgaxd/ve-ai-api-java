package br.com.fiap.veai.regiao;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/regioes")
@Tag(name = "Regiões", description = "Bairros atendidos pela rede de apoio")
public class RegiaoController {

    private final RegiaoService service;

    public RegiaoController(RegiaoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista as regiões atendidas")
    public List<RegiaoResposta> listar() {
        return service.listar().stream().map(RegiaoResposta::de).toList();
    }
}
