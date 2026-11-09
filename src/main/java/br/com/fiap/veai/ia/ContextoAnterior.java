package br.com.fiap.veai.ia;

/**
 * Modo automático: o que a pessoa acabou de ouvir (a descricaoCompleta da última descrição falada)
 * e há quantos segundos. Com isso o modelo fala só o que mudou e responde {@code novidade}.
 */
public record ContextoAnterior(String descricaoCompleta, long segundosAtras) {}
