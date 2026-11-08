package br.com.fiap.veai.ia;

/** Perigo imediato, falado antes de qualquer outra informação. Ex.: "degrau para baixo", "frente", "cerca de 2 metros". */
public record Perigo(String descricao, String direcao, String distanciaAprox) {}
