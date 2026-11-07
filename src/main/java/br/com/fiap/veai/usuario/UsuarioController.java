package br.com.fiap.veai.usuario;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.veai.auth.UsuarioLogado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuarios/me")
@Tag(name = "Usuário", description = "Perfil e preferências de voz do usuário logado")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Dados do usuário logado (gestor inclui as regiões atendidas)")
    public UsuarioResposta me(UsuarioLogado logado) {
        return UsuarioResposta.de(service.buscar(logado.id()));
    }

    @PutMapping("/preferencias")
    @Operation(summary = "Atualiza velocidade da voz e pergunta livre")
    public UsuarioResposta preferencias(UsuarioLogado logado, @Valid @RequestBody PreferenciasRequest req) {
        return UsuarioResposta.de(service.atualizarPreferencias(logado.id(), req));
    }
}
