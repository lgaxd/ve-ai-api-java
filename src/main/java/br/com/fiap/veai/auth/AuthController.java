package br.com.fiap.veai.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.veai.auth.AuthDtos.LoginRequest;
import br.com.fiap.veai.auth.AuthDtos.RegistroRequest;
import br.com.fiap.veai.auth.AuthDtos.TokenResposta;
import br.com.fiap.veai.usuario.UsuarioResposta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação")
@SecurityRequirements
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/registrar")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma conta de cidadão", description = "Gestores não se cadastram: vêm do seed.")
    public UsuarioResposta registrar(@Valid @RequestBody RegistroRequest req) {
        return service.registrar(req);
    }

    @PostMapping("/login")
    @Operation(summary = "Entra com e-mail e senha e recebe o JWT (válido por 8 h)")
    public TokenResposta login(@Valid @RequestBody LoginRequest req) {
        return service.login(req);
    }
}
