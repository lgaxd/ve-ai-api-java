package br.com.fiap.veai.relato;

import java.time.Instant;

import br.com.fiap.veai.regiao.Regiao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs de relato. */
public final class RelatoDtos {

    private RelatoDtos() {}

    public record RelatoRequest(
            @NotNull(message = "Informe o tipo da barreira.")
            TipoRelato tipo,

            @NotNull(message = "Informe a gravidade.")
            Gravidade gravidade,

            @NotBlank(message = "Descreva o problema.")
            @Size(max = 1000, message = "O texto pode ter no máximo 1000 caracteres.")
            @Schema(example = "Buraco grande na calçada, em frente ao número 500.")
            String texto,

            @NotNull(message = "A localização é obrigatória.")
            @DecimalMin(value = "-90.0", message = "Latitude inválida.")
            @DecimalMax(value = "90.0", message = "Latitude inválida.")
            @Schema(example = "-23.56612")
            Double latitude,

            @NotNull(message = "A localização é obrigatória.")
            @DecimalMin(value = "-180.0", message = "Longitude inválida.")
            @DecimalMax(value = "180.0", message = "Longitude inválida.")
            @Schema(example = "-46.68431")
            Double longitude,

            @Size(max = 200, message = "O endereço pode ter no máximo 200 caracteres.")
            @Schema(example = "Rua dos Pinheiros, 500")
            String endereco,

            @Schema(description = "Descrição da IA que originou o relato (opcional)")
            Long descricaoId,

            @Schema(description = "Opcional: se ausente, a API usa a região de centro mais próximo")
            Long regiaoId) {}

    public record RelatoEdicaoRequest(
            @NotNull(message = "Informe o tipo da barreira.")
            TipoRelato tipo,

            @NotNull(message = "Informe a gravidade.")
            Gravidade gravidade,

            @NotBlank(message = "Descreva o problema.")
            @Size(max = 1000, message = "O texto pode ter no máximo 1000 caracteres.")
            String texto,

            @Size(max = 200, message = "O endereço pode ter no máximo 200 caracteres.")
            String endereco) {}

    public record StatusRequest(
            @NotNull(message = "Informe o novo status.")
            StatusRelato status,

            @Size(max = 40, message = "O protocolo pode ter no máximo 40 caracteres.")
            @Schema(description = "Obrigatório para ENCAMINHADO", example = "SP156-2026-0012345")
            String protocoloOrgao,

            @Size(max = 500, message = "O comentário pode ter no máximo 500 caracteres.")
            String comentario,

            @Size(max = 300, message = "O motivo pode ter no máximo 300 caracteres.")
            @Schema(description = "Obrigatório para DESCARTADO")
            String motivoDescarte) {}

    public record RegiaoResumo(Long id, String nome) {
        static RegiaoResumo de(Regiao r) {
            return new RegiaoResumo(r.getId(), r.getNome());
        }
    }

    public record RelatoResposta(
            Long id,
            Long cidadaoId,
            Long descricaoId,
            RegiaoResumo regiao,
            TipoRelato tipo,
            Gravidade gravidade,
            String texto,
            double latitude,
            double longitude,
            String endereco,
            StatusRelato status,
            String protocoloOrgao,
            String motivoDescarte,
            int totalApoios,
            int prioridade,
            String normaReferencia,
            Instant criadoEm,
            Instant atualizadoEm) {

        public static RelatoResposta de(Relato r) {
            return new RelatoResposta(r.getId(), r.getCidadao().getId(),
                    r.getDescricao() == null ? null : r.getDescricao().getId(), RegiaoResumo.de(r.getRegiao()),
                    r.getTipo(), r.getGravidade(), r.getTexto(), r.getLatitude(), r.getLongitude(), r.getEndereco(),
                    r.getStatus(), r.getProtocoloOrgao(), r.getMotivoDescarte(), r.getTotalApoios(), r.getPrioridade(),
                    r.getNormaReferencia(), r.getCriadoEm(), r.getAtualizadoEm());
        }
    }

    /** O mesmo resultado do prc_registrar_relato_ou_apoio do Oracle. */
    public enum ResultadoRegistro { CRIADO, APOIO_REGISTRADO, JA_REGISTRADO }

    @Schema(description = "201 quando cria o relato; 200 quando já existe um igual a até 30 m")
    public record RegistroRelatoResposta(ResultadoRegistro resultado, boolean apoioRegistrado, RelatoResposta relato) {}

    public record AtualizacaoResposta(Long id, StatusRelato statusAnterior, StatusRelato statusNovo, String comentario,
                                      Long gestorId, String gestorNome, Instant criadoEm) {

        public static AtualizacaoResposta de(AtualizacaoRelato a) {
            return new AtualizacaoResposta(a.getId(), a.getStatusAnterior(), a.getStatusNovo(), a.getComentario(),
                    a.getGestor().getId(), a.getGestor().getNome(), a.getCriadoEm());
        }
    }
}
