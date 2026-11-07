package br.com.fiap.veai.relato;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApoioRelatoRepository extends JpaRepository<ApoioRelato, Long> {

    boolean existsByRelatoIdAndUsuarioId(Long relatoId, Long usuarioId);

    @Query("select a.usuario.id from ApoioRelato a where a.relato.id = :relatoId")
    List<Long> idsDosApoiadores(Long relatoId);
}
