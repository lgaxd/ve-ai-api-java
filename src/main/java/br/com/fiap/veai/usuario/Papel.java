package br.com.fiap.veai.usuario;

public enum Papel {
    CIDADAO, GESTOR;

    /** Nome da authority usada pelo Spring Security ({@code ROLE_CIDADAO}, {@code ROLE_GESTOR}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
