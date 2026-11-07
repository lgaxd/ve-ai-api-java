package br.com.fiap.veai.comum;

/** Cálculos geográficos simples, sem extensões espaciais (funciona igual em PostgreSQL e SQL Server). */
public final class Geo {

    /** Raio médio da Terra, o mesmo usado na função PL/SQL fn_distancia_metros. */
    public static final double RAIO_TERRA_METROS = 6_371_000d;

    /** Graus de latitude correspondentes a 1 metro (aprox. 1 / 111.320). */
    private static final double GRAUS_POR_METRO = 1d / 111_320d;

    private Geo() {}

    /** Distância em metros entre dois pontos pela fórmula de Haversine. */
    public static double distanciaMetros(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RAIO_TERRA_METROS * Math.asin(Math.min(1d, Math.sqrt(a)));
    }

    /** Margem em graus de latitude para um raio em metros (pré-filtro por "caixa"). */
    public static double margemLatitude(double metros) {
        return metros * GRAUS_POR_METRO;
    }

    /** Margem em graus de longitude: o grau de longitude encolhe com o cosseno da latitude. */
    public static double margemLongitude(double metros, double latitude) {
        return metros * GRAUS_POR_METRO / Math.max(0.01, Math.cos(Math.toRadians(latitude)));
    }
}
