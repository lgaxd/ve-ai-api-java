package br.com.fiap.veai.regiao;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.veai.comum.ApiException;
import br.com.fiap.veai.comum.Geo;

@Service
@Transactional(readOnly = true)
public class RegiaoService {

    private final RegiaoRepository repository;

    public RegiaoService(RegiaoRepository repository) {
        this.repository = repository;
    }

    public List<Regiao> listar() {
        return repository.findAll(Sort.by("nome"));
    }

    public Regiao buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.naoEncontrado("Região não encontrada."));
    }

    /** Região cujo centro está mais perto do ponto informado. */
    public Regiao maisProxima(double latitude, double longitude) {
        return maisProxima(listar(), latitude, longitude)
                .orElseThrow(() -> ApiException.regra("Nenhuma região de atendimento cadastrada."));
    }

    public static Optional<Regiao> maisProxima(List<Regiao> regioes, double latitude, double longitude) {
        return regioes.stream().filter(Regiao::temCentro).min(Comparator.comparingDouble(
                r -> Geo.distanciaMetros(latitude, longitude, r.getLatitude(), r.getLongitude())));
    }
}
