package br.com.fiap.veai.relato;

import java.util.EnumSet;
import java.util.Set;

/**
 * Máquina de estados do relato (a mesma do prc_atualizar_status no Oracle):
 * ABERTO → EM_TRIAGEM → ENCAMINHADO → RESOLVIDO, sem pular etapa e sem voltar;
 * DESCARTADO a partir de qualquer status não final. RESOLVIDO e DESCARTADO são finais.
 */
public enum StatusRelato {
    ABERTO, EM_TRIAGEM, ENCAMINHADO, RESOLVIDO, DESCARTADO;

    /** Status que ainda estão "abertos" para deduplicação e prioridade. */
    public static final Set<StatusRelato> NAO_FINAIS = EnumSet.of(ABERTO, EM_TRIAGEM, ENCAMINHADO);

    public boolean ehFinal() {
        return this == RESOLVIDO || this == DESCARTADO;
    }

    public boolean podeIrPara(StatusRelato novo) {
        if (ehFinal() || novo == null || novo == this) {
            return false;
        }
        if (novo == DESCARTADO) {
            return true;
        }
        return novo.ordinal() == this.ordinal() + 1;
    }
}
