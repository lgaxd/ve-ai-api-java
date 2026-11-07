package br.com.fiap.veai.comum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.fiap.veai.regiao.Regiao;
import br.com.fiap.veai.regiao.RegiaoService;

class GeoTest {

    @Test
    @DisplayName("Haversine: mesmo ponto = 0 m; pontos do seed a ~10 m; Sé–Pinheiros ~5,7 km")
    void distancias() {
        assertThat(Geo.distanciaMetros(-23.5661, -46.6843, -23.5661, -46.6843)).isZero();
        // relato #1 do seed e o ponto usado na demonstração do apoio
        assertThat(Geo.distanciaMetros(-23.56612, -46.68431, -23.56620, -46.68436)).isBetween(8.0, 12.0);
        assertThat(Geo.distanciaMetros(-23.5503, -46.6339, -23.5662, -46.6890)).isCloseTo(5_900, within(300.0));
    }

    @Test
    @DisplayName("a caixa do pré-filtro sempre contém o círculo de 30 m")
    void caixaContemOCirculo() {
        double lat = -23.55;
        double dLat = Geo.margemLatitude(30);
        double dLon = Geo.margemLongitude(30, lat);
        assertThat(Geo.distanciaMetros(lat, -46.63, lat + dLat, -46.63)).isGreaterThanOrEqualTo(29.9);
        assertThat(Geo.distanciaMetros(lat, -46.63, lat, -46.63 + dLon)).isGreaterThanOrEqualTo(29.9);
    }

    @Test
    @DisplayName("região do relato = centro mais próximo")
    void regiaoMaisProxima() {
        Regiao pinheiros = new Regiao("Pinheiros", "São Paulo", "SP", -23.5662, -46.6890);
        Regiao se = new Regiao("Sé", "São Paulo", "SP", -23.5503, -46.6339);
        Regiao vilaMariana = new Regiao("Vila Mariana", "São Paulo", "SP", -23.5889, -46.6355);
        List<Regiao> todas = List.of(pinheiros, se, vilaMariana);

        assertThat(RegiaoService.maisProxima(todas, -23.56612, -46.68431)).contains(pinheiros); // Rua dos Pinheiros
        assertThat(RegiaoService.maisProxima(todas, -23.55052, -46.63331)).contains(se);        // Praça da Sé
        assertThat(RegiaoService.maisProxima(todas, -23.5889, -46.6342)).contains(vilaMariana); // Domingos de Morais
        assertThat(RegiaoService.maisProxima(List.of(), 0, 0)).isEmpty();
    }
}
