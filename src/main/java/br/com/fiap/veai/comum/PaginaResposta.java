package br.com.fiap.veai.comum;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Página no formato do contrato (o {@code Page} do Spring não é serializado diretamente). */
public record PaginaResposta<T>(List<T> conteudo, int pagina, int tamanho, long totalElementos, int totalPaginas) {

    public static <E, T> PaginaResposta<T> de(Page<E> page, Function<E, T> mapeador) {
        return new PaginaResposta<>(page.map(mapeador).getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
