package br.com.fiap.veai.relato;

import java.time.Duration;
import java.time.Instant;

/**
 * Prioridade na fila do gestor (mesma regra da fn_prioridade_relato do Oracle):
 * peso da gravidade (1/3/5) × (1 + total de apoios) + dias em aberto. Status final = 0.
 */
public final class CalculadoraPrioridade {

    private CalculadoraPrioridade() {}

    public static int calcular(Gravidade gravidade, int totalApoios, StatusRelato status, Instant criadoEm, Instant agora) {
        if (status.ehFinal()) {
            return 0;
        }
        long diasEmAberto = Math.max(0, Duration.between(criadoEm, agora).toDays());
        return gravidade.peso() * (1 + Math.max(0, totalApoios)) + (int) diasEmAberto;
    }
}
