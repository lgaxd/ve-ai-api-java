package br.com.fiap.veai.regiao;

public record RegiaoResposta(Long id, String nome, String cidade, String uf, double latitude, double longitude) {

    public static RegiaoResposta de(Regiao r) {
        return new RegiaoResposta(r.getId(), r.getNome(), r.getCidade(), r.getUf(), r.getLatitude(), r.getLongitude());
    }
}
