package br.com.fiap.veai.ia;

/** Perigo imediato, falado antes de qualquer outra informação. Ex.: "degrau descendo", "frente", "2 m". */
public record Perigo(String descricao, String direcao, String distanciaAprox) {}
