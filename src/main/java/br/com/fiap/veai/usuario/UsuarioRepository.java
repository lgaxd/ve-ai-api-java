package br.com.fiap.veai.usuario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "regioes")
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> buscarComRegioes(Long id);

    @Query("""
            select u.id from Usuario u join u.regioes r
            where u.papel = br.com.fiap.veai.usuario.Papel.GESTOR and r.id = :regiaoId
            """)
    List<Long> idsDosGestoresDaRegiao(Long regiaoId);
}
