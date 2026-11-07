package br.com.fiap.veai.comum;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Converte os parâmetros {@code page}/{@code size} do contrato, limitando o tamanho a 50. */
public final class Paginacao {

    public static final int TAMANHO_PADRAO = 10;
    public static final int TAMANHO_MAXIMO = 50;

    private Paginacao() {}

    public static Pageable de(Integer page, Integer size, Sort ordem) {
        int pagina = page == null || page < 0 ? 0 : page;
        int tamanho = size == null || size < 1 ? TAMANHO_PADRAO : Math.min(size, TAMANHO_MAXIMO);
        return PageRequest.of(pagina, tamanho, ordem);
    }
}
