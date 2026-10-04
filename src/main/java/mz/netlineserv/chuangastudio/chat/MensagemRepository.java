package mz.netlineserv.chuangastudio.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MensagemRepository extends JpaRepository<Mensagem, UUID> {

    /** Página de histórico: as mais recentes antes de um instante (scroll para cima). */
    @Query("SELECT m FROM Mensagem m JOIN FETCH m.remetente WHERE m.conversa.id = :conversa AND m.deletado = false " +
            "AND m.criadoEm < :antes ORDER BY m.criadoEm DESC")
    List<Mensagem> anteriores(@Param("conversa") UUID conversa, @Param("antes") LocalDateTime antes, Pageable pageable);

    /** Desde um instante até agora (para saltar para uma mensagem antiga, ex. resultado da pesquisa). */
    @Query("SELECT m FROM Mensagem m JOIN FETCH m.remetente WHERE m.conversa.id = :conversa AND m.deletado = false " +
            "AND m.criadoEm >= :desde ORDER BY m.criadoEm ASC")
    List<Mensagem> aPartirDe(@Param("conversa") UUID conversa, @Param("desde") LocalDateTime desde, Pageable pageable);

    /** Sincronização incremental: novas ou alteradas (editadas/apagadas/reações) desde o último pedido. */
    @Query("SELECT m FROM Mensagem m JOIN FETCH m.remetente WHERE m.conversa.id = :conversa AND m.deletado = false " +
            "AND (m.criadoEm > :desde OR m.atualizadoEm > :desde) ORDER BY m.criadoEm ASC")
    List<Mensagem> alteradasDesde(@Param("conversa") UUID conversa, @Param("desde") LocalDateTime desde);

    @Query("SELECT m FROM Mensagem m JOIN FETCH m.remetente WHERE m.id IN :ids")
    List<Mensagem> porIds(@Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT m FROM Mensagem m JOIN FETCH m.remetente WHERE m.conversa.id = :conversa AND m.deletado = false " +
            "AND m.apagada = false AND m.tipo IN :tipos ORDER BY m.criadoEm DESC",
            countQuery = "SELECT COUNT(m) FROM Mensagem m WHERE m.conversa.id = :conversa AND m.deletado = false " +
                    "AND m.apagada = false AND m.tipo IN :tipos")
    Page<Mensagem> porTipo(@Param("conversa") UUID conversa, @Param("tipos") Collection<Mensagem.Tipo> tipos, Pageable pageable);

    @Query(value = "SELECT m FROM Mensagem m JOIN FETCH m.remetente WHERE m.conversa.id = :conversa AND m.deletado = false " +
            "AND m.apagada = false AND LOWER(m.conteudo) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY m.criadoEm DESC",
            countQuery = "SELECT COUNT(m) FROM Mensagem m WHERE m.conversa.id = :conversa AND m.deletado = false " +
                    "AND m.apagada = false AND LOWER(m.conteudo) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Mensagem> pesquisar(@Param("conversa") UUID conversa, @Param("q") String q, Pageable pageable);

    /** Marca a mensagem como alterada para a sincronização a apanhar (reações). */
    @Modifying
    @Query(value = "UPDATE mensagens SET atualizado_em = NOW() WHERE id = :id", nativeQuery = true)
    void tocar(@Param("id") UUID id);

    // --- Reações ---

    @Modifying
    @Query(value = "INSERT INTO mensagem_reacoes (mensagem_id, usuario_id, emoji) VALUES (:mensagem, :usuario, :emoji) " +
            "ON CONFLICT (mensagem_id, usuario_id) DO UPDATE SET emoji = :emoji, criado_em = NOW()", nativeQuery = true)
    void reagir(@Param("mensagem") UUID mensagem, @Param("usuario") UUID usuario, @Param("emoji") String emoji);

    @Modifying
    @Query(value = "DELETE FROM mensagem_reacoes WHERE mensagem_id = :mensagem AND usuario_id = :usuario", nativeQuery = true)
    void retirarReacao(@Param("mensagem") UUID mensagem, @Param("usuario") UUID usuario);

    /** [mensagemId, emoji, usuarioId, nome] */
    @Query(value = "SELECT CAST(r.mensagem_id AS VARCHAR), r.emoji, CAST(r.usuario_id AS VARCHAR), u.nome FROM mensagem_reacoes r " +
            "JOIN usuarios u ON u.id = r.usuario_id WHERE r.mensagem_id IN :ids ORDER BY r.criado_em", nativeQuery = true)
    List<Object[]> reacoes(@Param("ids") Collection<UUID> ids);

    // --- Favoritas ---

    @Modifying
    @Query(value = "INSERT INTO mensagem_favoritas (mensagem_id, usuario_id) VALUES (:mensagem, :usuario) ON CONFLICT DO NOTHING", nativeQuery = true)
    void favoritar(@Param("mensagem") UUID mensagem, @Param("usuario") UUID usuario);

    @Modifying
    @Query(value = "DELETE FROM mensagem_favoritas WHERE mensagem_id = :mensagem AND usuario_id = :usuario", nativeQuery = true)
    void desfavoritar(@Param("mensagem") UUID mensagem, @Param("usuario") UUID usuario);

    @Query(value = "SELECT CAST(mensagem_id AS VARCHAR) FROM mensagem_favoritas WHERE usuario_id = :usuario AND mensagem_id IN :ids", nativeQuery = true)
    List<String> favoritasEntre(@Param("usuario") UUID usuario, @Param("ids") Collection<UUID> ids);

    /** Ids das favoritas do utilizador (mais recentes primeiro). */
    @Query(value = "SELECT CAST(f.mensagem_id AS VARCHAR) FROM mensagem_favoritas f JOIN mensagens m ON m.id = f.mensagem_id " +
            "WHERE f.usuario_id = :usuario AND m.deletado = false AND m.apagada = false ORDER BY f.criado_em DESC LIMIT 200", nativeQuery = true)
    List<String> idsFavoritas(@Param("usuario") UUID usuario);

    // --- Apagar para mim ---

    @Modifying
    @Query(value = "INSERT INTO mensagem_ocultas (mensagem_id, usuario_id) VALUES (:mensagem, :usuario) ON CONFLICT DO NOTHING", nativeQuery = true)
    void ocultar(@Param("mensagem") UUID mensagem, @Param("usuario") UUID usuario);

    @Query(value = "SELECT CAST(mensagem_id AS VARCHAR) FROM mensagem_ocultas WHERE usuario_id = :usuario AND mensagem_id IN :ids", nativeQuery = true)
    List<String> ocultasEntre(@Param("usuario") UUID usuario, @Param("ids") Collection<UUID> ids);

    // --- Anexos (sem carregar os bytes) ---

    /** [anexoId, contentType, nome, tamanho] */
    @Query(value = "SELECT CAST(id AS VARCHAR), content_type, nome, tamanho FROM chat_anexos WHERE id IN :ids", nativeQuery = true)
    List<Object[]> metadadosAnexos(@Param("ids") Collection<UUID> ids);
}
