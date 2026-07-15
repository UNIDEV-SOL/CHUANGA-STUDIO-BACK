package mz.netlineserv.chuangastudio.repository;

import mz.netlineserv.chuangastudio.domain.entity.Marcacao;
import mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MarcacaoRepository extends JpaRepository<Marcacao, UUID> {

    List<Marcacao> findByClienteIdAndDeletadoFalseOrderByDataMarcacaoDescHoraMarcacaoDesc(UUID clienteId);

    List<Marcacao> findByDeletadoFalseOrderByDataMarcacaoDescHoraMarcacaoDesc();

    List<Marcacao> findByStatusAndDeletadoFalseOrderByDataMarcacaoDescHoraMarcacaoDesc(StatusMarcacao status);

    @Query("SELECT DISTINCT m FROM Marcacao m " +
            "JOIN FETCH m.cliente " +
            "LEFT JOIN FETCH m.servicos " +
            "WHERE m.cliente.id = :clienteId AND m.deletado = false " +
            "ORDER BY m.dataMarcacao DESC, m.horaMarcacao DESC")
    List<Marcacao> buscarDoClienteComDetalhes(@Param("clienteId") UUID clienteId);

    long countByDataMarcacaoAndDeletadoFalse(LocalDate data);

    @Query("select coalesce(sum(m.valorTotal), 0) from Marcacao m where m.deletado = false " +
           "and m.status = mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao.CONCLUIDA " +
           "and m.dataMarcacao between :inicio and :fim")
    java.math.BigDecimal somarReceitaEntre(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
