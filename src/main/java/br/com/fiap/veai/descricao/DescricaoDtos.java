package br.com.fiap.veai.descricao;

import java.time.Instant;
import java.util.List;

import br.com.fiap.veai.ia.Perigo;
import br.com.fiap.veai.relato.Gravidade;
import br.com.fiap.veai.relato.TipoRelato;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** DTOs de descrição. */
public final class DescricaoDtos {

    private DescricaoDtos() {}

    @Schema(description = "Descrição gerada pela IA. O app fala a descricaoCompleta, que já começa pelos perigos (com posição e distância). perigos, resumo e objetos são dados estruturados para alertas, histórico e listas.")
    public record DescricaoResposta(
            Long id,
            ModoDescricao modo,
            List<Perigo> perigos,
            String resumo,
            String descricaoCompleta,
            List<String> objetos,
            boolean barreiraDetectada,
            TipoRelato barreiraTipo,
            int latenciaMs,
            Boolean util,
            Instant criadoEm,
            @Schema(description = "Presente só quando a IA viu uma barreira estrutural com confiança alta")
            SugestaoRelato sugestaoRelato) {

        public static DescricaoResposta de(Descricao d, SugestaoRelato sugestao) {
            return new DescricaoResposta(d.getId(), d.getModo(), d.getPerigos(), d.getResumo(), d.getDescricaoCompleta(),
                    d.getObjetos(), d.isBarreiraDetectada(), d.getBarreiraTipo(), d.getLatenciaMs(), d.getUtil(),
                    d.getCriadoEm(), sugestao);
        }
    }

    @Schema(description = "Pergunta que o app faz ao cidadão antes de criar o relato")
    public record SugestaoRelato(TipoRelato tipo, Gravidade gravidade, String pergunta) {}

    public record AvaliacaoRequest(@NotNull(message = "Informe se a descrição foi útil.") Boolean util) {}

    public record PerguntaResposta(String resposta) {}
}
