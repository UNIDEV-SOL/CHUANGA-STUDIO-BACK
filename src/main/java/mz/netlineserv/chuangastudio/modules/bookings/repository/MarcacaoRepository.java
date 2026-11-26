package mz.netlineserv.chuangastudio.modules.bookings.repository;

import mz.netlineserv.chuangastudio.modules.bookings.domain.Marcacao;
import mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarcacaoRepository extends JpaRepository<Marcacao, UUID>, JpaSpecificationExecutor<Marcacao> {

    List<Marcacao> findByClienteIdAndDeletadoFalseOrderByDataMarcacaoDescHoraMarcacaoDesc(UUID clienteId);

    List<Marcacao> findByDeletadoFalseOrderByDataMarcacaoDescHoraMarcacaoDesc();

    List<Marcacao> findByStatusAndDeletadoFalseOrderByDataMarcacaoDescHoraMarcacaoDesc(StatusMarcacao status);

    @Query("SELECT DISTINCT m FROM Marcacao m " +
            "JOIN FETCH m.cliente " +
            "LEFT JOIN FETCH m.servicos " +
            "WHERE m.cliente.id = :clienteId AND m.deletado = false " +
            "ORDER BY m.dataMarcacao DESC, m.horaMarcacao DESC")
    List<Marcacao> buscarDoClienteComDetalhes(@Param("clienteId") UUID clienteId);

    @Query("SELECT DISTINCT m FROM Marcacao m " +
            "JOIN FETCH m.cliente " +
            "LEFT JOIN FETCH m.servicos " +
            "WHERE m.deletado = false " +
            "ORDER BY m.dataMarcacao DESC, m.horaMarcacao DESC")
    List<Marcacao> buscarTodasComClienteEServicos();

    @Query("SELECT DISTINCT m FROM Marcacao m " +
            "LEFT JOIN FETCH m.servicos ms " +
            "LEFT JOIN FETCH ms.servico " +
            "WHERE m.dataMarcacao = :data AND m.deletado = false AND m.status IN :status")
    List<Marcacao> buscarNaDataComServicos(@Param("data") LocalDate data,
                                           @Param("status") Collection<StatusMarcacao> status);

    @Query("SELECT m FROM Marcacao m " +
            "JOIN FETCH m.cliente " +
            "LEFT JOIN FETCH m.servicos " +
            "WHERE m.id = :id AND m.deletado = false")
    Optional<Marcacao> buscarPorIdComDetalhes(@Param("id") UUID id);

    /** Candidatas a lembrete: confirmadas, sem lembrete, nos dias indicados (a hora exata filtra-se em Java). */
    @Query("SELECT DISTINCT m FROM Marcacao m " +
            "JOIN FETCH m.cliente " +
            "LEFT JOIN FETCH m.servicos " +
            "WHERE m.deletado = false AND m.lembreteEnviadoEm IS NULL " +
            "AND m.status = mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao.CONFIRMADA " +
            "AND m.dataMarcacao BETWEEN :inicio AND :fim")
    List<Marcacao> buscarSemLembreteEntre(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT DISTINCT m FROM Marcacao m " +
            "JOIN FETCH m.cliente " +
            "LEFT JOIN FETCH m.servicos ms " +
            "LEFT JOIN FETCH ms.servico " +
            "WHERE m.deletado = false AND m.dataMarcacao = :data AND m.status IN :status " +
            "ORDER BY m.horaMarcacao")
    List<Marcacao> buscarAgendaDoDia(@Param("data") LocalDate data, @Param("status") Collection<StatusMarcacao> status);

    long countByDataMarcacaoAndDeletadoFalse(LocalDate data);

    /** Concluídas no intervalo e ainda sem lembrete de manutenção (com serviços e o catálogo para ver os dias). */
    @Query("SELECT DISTINCT m FROM Marcacao m " +
            "JOIN FETCH m.cliente " +
            "LEFT JOIN FETCH m.servicos ms " +
            "LEFT JOIN FETCH ms.servico " +
            "WHERE m.deletado = false AND m.lembreteManutencaoEm IS NULL " +
            "AND m.status = mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao.CONCLUIDA " +
            "AND m.dataMarcacao BETWEEN :inicio AND :fim")
    List<Marcacao> buscarSemLembreteManutencao(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("SELECT COUNT(m) > 0 FROM Marcacao m WHERE m.cliente.id = :clienteId AND m.deletado = false " +
            "AND m.dataMarcacao > :data AND m.status IN :status")
    boolean clienteTemMarcacaoDepois(@Param("clienteId") UUID clienteId, @Param("data") LocalDate data,
                                     @Param("status") Collection<StatusMarcacao> status);

    /** [clienteId, nº de marcações, investido, faltas] para os clientes de uma página. */
    @Query("SELECT m.cliente.id, COUNT(m), " +
            "COALESCE(SUM(CASE WHEN m.status <> mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao.CANCELADA " +
            "AND m.status <> mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao.NAO_COMPARECEU THEN m.valorTotal ELSE 0 END), 0), " +
            "SUM(CASE WHEN m.status = mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao.NAO_COMPARECEU THEN 1 ELSE 0 END) " +
            "FROM Marcacao m WHERE m.deletado = false AND m.cliente.id IN :clientes GROUP BY m.cliente.id")
    List<Object[]> resumoPorCliente(@Param("clientes") Collection<UUID> clientes);

    @Query("select coalesce(sum(m.valorTotal), 0) from Marcacao m where m.deletado = false " +
           "and m.status = mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao.CONCLUIDA " +
           "and m.dataMarcacao between :inicio and :fim")
    java.math.BigDecimal somarReceitaEntre(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
