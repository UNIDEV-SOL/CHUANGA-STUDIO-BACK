package mz.netlineserv.chuangastudio.repository;

import mz.netlineserv.chuangastudio.domain.entity.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID>, JpaSpecificationExecutor<Notificacao> {

    @Query(value = "SELECT COUNT(*) FROM notificacoes n WHERE n.deletado = false AND NOT EXISTS " +
            "(SELECT 1 FROM notificacao_leituras l WHERE l.notificacao_id = n.id AND l.usuario_id = :usuario)",
            nativeQuery = true)
    long contarNaoLidas(@Param("usuario") UUID usuario);

    @Query(value = "SELECT CAST(l.notificacao_id AS VARCHAR) FROM notificacao_leituras l " +
            "WHERE l.usuario_id = :usuario AND l.notificacao_id IN :ids", nativeQuery = true)
    List<String> lidasEntre(@Param("usuario") UUID usuario, @Param("ids") Collection<UUID> ids);

    @Modifying
    @Query(value = "INSERT INTO notificacao_leituras (notificacao_id, usuario_id) VALUES (:notificacao, :usuario) " +
            "ON CONFLICT DO NOTHING", nativeQuery = true)
    void marcarLida(@Param("notificacao") UUID notificacao, @Param("usuario") UUID usuario);

    @Modifying
    @Query(value = "INSERT INTO notificacao_leituras (notificacao_id, usuario_id) " +
            "SELECT n.id, :usuario FROM notificacoes n WHERE n.deletado = false ON CONFLICT DO NOTHING", nativeQuery = true)
    void marcarTodasLidas(@Param("usuario") UUID usuario);
}
