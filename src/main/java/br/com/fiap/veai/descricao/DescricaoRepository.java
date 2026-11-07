package br.com.fiap.veai.descricao;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DescricaoRepository extends JpaRepository<Descricao, Long> {

    Page<Descricao> findByUsuarioId(Long usuarioId, Pageable pageable);

    Optional<Descricao> findByIdAndUsuarioId(Long id, Long usuarioId);
}
