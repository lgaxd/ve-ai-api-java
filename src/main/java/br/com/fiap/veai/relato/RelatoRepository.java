package br.com.fiap.veai.relato;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface RelatoRepository extends JpaRepository<Relato, Long> {

    /**
     * Pré-filtro da regra dos 30 m: relatos do mesmo tipo, ainda não finalizados, dentro de uma "caixa"
     * de latitude/longitude. A distância exata é calculada depois, com Haversine.
     * O bloqueio evita que dois apoios simultâneos percam um incremento de total_apoios.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from Relato r
            where r.tipo = :tipo and r.status in :status
              and r.latitude between :latMin and :latMax
              and r.longitude between :lonMin and :lonMax
            order by r.criadoEm
            """)
    List<Relato> candidatosProximos(TipoRelato tipo, Collection<StatusRelato> status,
                                    double latMin, double latMax, double lonMin, double lonMax);

    @EntityGraph(attributePaths = "regiao")
    Page<Relato> findByCidadaoId(Long cidadaoId, Pageable pageable);

    @EntityGraph(attributePaths = {"regiao", "cidadao"})
    @Query("select r from Relato r where r.id = :id")
    Optional<Relato> buscarCompleto(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Relato r where r.id = :id")
    Optional<Relato> buscarParaAtualizar(Long id);

    @EntityGraph(attributePaths = "regiao")
    Page<Relato> findByRegiaoIdIn(Collection<Long> regioes, Pageable pageable);

    @EntityGraph(attributePaths = "regiao")
    Page<Relato> findByRegiaoIdInAndStatus(Collection<Long> regioes, StatusRelato status, Pageable pageable);

    List<Relato> findByStatusIn(Collection<StatusRelato> status);
}
