package mz.netlineserv.chuangastudio.repository;

import mz.netlineserv.chuangastudio.domain.entity.Cotacao;
import mz.netlineserv.chuangastudio.domain.enums.StatusCotacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CotacaoRepository extends JpaRepository<Cotacao, UUID>, JpaSpecificationExecutor<Cotacao> {

    List<Cotacao> findByDeletadoFalseOrderByCriadoEmDesc();

    List<Cotacao> findByStatusAndDeletadoFalseOrderByCriadoEmDesc(StatusCotacao status);

    long countByDeletadoFalse();

    @Query("SELECT DISTINCT c FROM Cotacao c " +
            "LEFT JOIN FETCH c.servicos " +
            "WHERE c.deletado = false " +
            "ORDER BY c.criadoEm DESC")
    List<Cotacao> buscarTodasComServicos();

    @Query("SELECT DISTINCT c FROM Cotacao c " +
            "LEFT JOIN FETCH c.servicos " +
            "WHERE c.deletado = false AND c.status = :status " +
            "ORDER BY c.criadoEm DESC")
    List<Cotacao> buscarPorStatusComServicos(@Param("status") StatusCotacao status);

    @Query("SELECT c FROM Cotacao c " +
            "LEFT JOIN FETCH c.servicos " +
            "WHERE c.id = :id AND c.deletado = false")
    Optional<Cotacao> buscarPorIdComServicos(@Param("id") UUID id);
}