package br.com.fiap.veai.relato;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AtualizacaoRelatoRepository extends JpaRepository<AtualizacaoRelato, Long> {

    @EntityGraph(attributePaths = "gestor")
    List<AtualizacaoRelato> findByRelatoIdOrderByCriadoEmAscIdAsc(Long relatoId);
}
