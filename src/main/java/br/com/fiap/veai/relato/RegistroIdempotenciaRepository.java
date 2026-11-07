package br.com.fiap.veai.relato;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RegistroIdempotenciaRepository extends JpaRepository<RegistroIdempotencia, Long> {

    Optional<RegistroIdempotencia> findByUsuarioIdAndChaveAndCriadoEmAfter(Long usuarioId, String chave, Instant limite);

    @Modifying
    @Query("delete from RegistroIdempotencia r where r.criadoEm < :limite")
    int apagarAnterioresA(Instant limite);
}
