package br.com.fiap.veai.auth;

import java.time.Instant;

import br.com.fiap.veai.usuario.Papel;
import br.com.fiap.veai.usuario.UsuarioResposta;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTOs de autenticação. */
public final class AuthDtos {

    private AuthDtos() {}

    public record RegistroRequest(
            @NotBlank(message = "Informe seu nome.")
            @Size(max = 120, message = "O nome pode ter no máximo 120 caracteres.")
            String nome,

            @NotBlank(message = "Informe seu e-mail.")
            @Email(message = "E-mail inválido.")
            @Size(max = 160, message = "O e-mail pode ter no máximo 160 caracteres.")
            String email,

            @NotBlank(message = "Informe uma senha.")
            @Size(min = 8, max = 72, message = "A senha precisa ter entre 8 e 72 caracteres.")
            @Schema(example = "Veai@2026")
            String senha) {}

    public record LoginRequest(
            @NotBlank(message = "Informe seu e-mail.")
            @Email(message = "E-mail inválido.")
            @Schema(example = "ana.souza@veai.dev")
            String email,

            @NotBlank(message = "Informe sua senha.")
            @Schema(example = "Veai@2026")
            String senha) {}

    public record TokenResposta(String token, String tipo, Instant expiraEm, Papel papel, UsuarioResposta usuario) {}
}
