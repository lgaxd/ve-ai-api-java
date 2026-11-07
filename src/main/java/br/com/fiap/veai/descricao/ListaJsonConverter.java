package br.com.fiap.veai.descricao;

import java.util.ArrayList;
import java.util.List;

import br.com.fiap.veai.ia.Perigo;
import jakarta.persistence.AttributeConverter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grava listas como texto JSON. É o formato das colunas {@code perigos}/{@code objetos}:
 * {@code CLOB CHECK (... IS JSON)} no Oracle (mesmo DDL do repositório database) e
 * {@code NVARCHAR(MAX) CHECK (ISJSON(...) = 1)} no SQL Server.
 *
 * <p>Não usamos {@code @JdbcTypeCode(SqlTypes.JSON)} porque, no Oracle 21+, o Hibernate grava
 * o tipo JSON nativo (binário OSON) e, no 19c (FIAP), um BLOB; nenhum dos dois cabe numa coluna CLOB.
 */
public abstract class ListaJsonConverter<T> implements AttributeConverter<List<T>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final TypeReference<List<T>> tipo;

    protected ListaJsonConverter(TypeReference<List<T>> tipo) {
        this.tipo = tipo;
    }

    @Override
    public String convertToDatabaseColumn(List<T> lista) {
        return JSON.writeValueAsString(lista == null ? List.of() : lista);
    }

    @Override
    public List<T> convertToEntityAttribute(String texto) {
        if (texto == null || texto.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(JSON.readValue(texto, tipo));
    }

    public static final class Perigos extends ListaJsonConverter<Perigo> {
        public Perigos() {
            super(new TypeReference<>() {});
        }
    }

    public static final class Textos extends ListaJsonConverter<String> {
        public Textos() {
            super(new TypeReference<>() {});
        }
    }
}
