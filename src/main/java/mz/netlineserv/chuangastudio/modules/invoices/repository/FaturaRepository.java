package mz.netlineserv.chuangastudio.modules.invoices.repository;

import mz.netlineserv.chuangastudio.modules.invoices.domain.Fatura;
import mz.netlineserv.chuangastudio.modules.invoices.domain.StatusFatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FaturaRepository extends JpaRepository<Fatura, UUID>, JpaSpecificationExecutor<Fatura> {

    String COM_DETALHES = "SELECT DISTINCT f FROM Fatura f " +
            "JOIN FETCH f.marcacao m " +
            "JOIN FETCH m.cliente " +
            "LEFT JOIN FETCH f.itens ";

    @Query(COM_DETALHES + "WHERE f.id = :id AND f.deletado = false")
    Optional<Fatura> buscarPorIdComDetalhes(@Param("id") UUID id);

    @Query(COM_DETALHES + "WHERE f.tokenPublico = :token AND f.deletado = false")
    Optional<Fatura> buscarPorTokenComDetalhes(@Param("token") String token);

    @Query(COM_DETALHES + "WHERE f.deletado = false ORDER BY f.dataEmissao DESC")
    List<Fatura> buscarTodasComDetalhes();

    @Query(COM_DETALHES + "WHERE m.cliente.id = :clienteId AND f.deletado = false " +
            "AND f.status <> mz.netlineserv.chuangastudio.modules.invoices.domain.StatusFatura.ANULADA " +
            "ORDER BY f.dataEmissao DESC")
    List<Fatura> buscarDoClienteComDetalhes(@Param("clienteId") UUID clienteId);

    boolean existsByMarcacaoIdAndStatusNotAndDeletadoFalse(UUID marcacaoId, StatusFatura status);

    /** Fatura válida de cada marcação de uma página (para a lista do painel). */
    @Query("SELECT f FROM Fatura f WHERE f.marcacao.id IN :ids AND f.deletado = false " +
            "AND f.status <> mz.netlineserv.chuangastudio.modules.invoices.domain.StatusFatura.ANULADA")
    List<Fatura> buscarValidasDasMarcacoes(@Param("ids") java.util.Collection<UUID> ids);

    /** Visitas para a fidelização: atendimentos faturados (sem anuladas). */
    @Query("SELECT COUNT(f) FROM Fatura f WHERE f.marcacao.cliente.id = :cliente AND f.deletado = false " +
            "AND f.status <> mz.netlineserv.chuangastudio.modules.invoices.domain.StatusFatura.ANULADA")
    long contarVisitas(@Param("cliente") UUID cliente);

    @Query("SELECT COUNT(f) FROM Fatura f WHERE f.marcacao.cliente.id = :cliente AND f.deletado = false " +
            "AND f.status <> mz.netlineserv.chuangastudio.modules.invoices.domain.StatusFatura.ANULADA AND f.descontoFidelizacao > 0")
    long contarRecompensasUsadas(@Param("cliente") UUID cliente);

    @Query(value = "SELECT nextval('fatura_numero_seq')", nativeQuery = true)
    long proximoNumero();
}
