package br.com.saneia.api.report;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** Relatos visíveis do morador. Os recorrentes em aberto vêm primeiro (mesma ordem do app). */
    @Query(value = "select r from Report r where r.owner.id = :ownerId and r.deletedAt is null "
            + "order by case when r.status <> :closed and r.previousReport is not null then 0 else 1 end, "
            + "r.createdAt desc, r.id desc",
            countQuery = "select count(r) from Report r where r.owner.id = :ownerId and r.deletedAt is null")
    Page<Report> findVisibleByOwner(@Param("ownerId") Long ownerId, @Param("closed") ReportStatus closed,
            Pageable pageable);

    /** Fila da operadora: todos os relatos não excluídos, com prioridade por recorrência. */
    @Query(value = "select r from Report r where r.deletedAt is null "
            + "order by case when r.status <> :closed and r.previousReport is not null then 0 else 1 end, "
            + "r.createdAt desc, r.id desc",
            countQuery = "select count(r) from Report r where r.deletedAt is null")
    Page<Report> findQueue(@Param("closed") ReportStatus closed, Pageable pageable);

    Optional<Report> findByIdAndDeletedAtIsNull(Long id);

    /** Inclui excluídos: a exclusão não libera o intervalo de 20 dias. */
    List<Report> findByOwnerIdAndCategoryAndStatusNot(Long ownerId, String category, ReportStatus status);

    boolean existsByProtocol(String protocol);
}
