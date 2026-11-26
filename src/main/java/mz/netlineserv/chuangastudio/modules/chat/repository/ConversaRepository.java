package mz.netlineserv.chuangastudio.modules.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mz.netlineserv.chuangastudio.modules.chat.domain.Conversa;

public interface ConversaRepository extends JpaRepository<Conversa, UUID>, JpaSpecificationExecutor<Conversa> {

    Optional<Conversa> findFirstByTipoAndClienteIdAndDeletadoFalse(Conversa.Tipo tipo, UUID clienteId);

    Optional<Conversa> findFirstBySistemaAndDeletadoFalse(Conversa.Sistema sistema);

    Optional<Conversa> findFirstByServicoIdAndDeletadoFalse(UUID servicoId);

    /** [conversaId, nº de mensagens por ler] para um utilizador (mensagens de outros depois da sua última leitura). */
    @Query(value = "SELECT CAST(m.conversa_id AS VARCHAR), COUNT(*) FROM mensagens m " +
            "LEFT JOIN conversa_leituras l ON l.conversa_id = m.conversa_id AND l.usuario_id = :usuario " +
            "WHERE m.conversa_id IN :conversas AND m.deletado = false AND m.apagada = false AND m.remetente_id <> :usuario " +
            "AND m.tipo <> 'SISTEMA' AND (l.lida_ate IS NULL OR m.criado_em > l.lida_ate) GROUP BY m.conversa_id", nativeQuery = true)
    List<Object[]> naoLidasPorConversa(@Param("usuario") UUID usuario, @Param("conversas") Collection<UUID> conversas);

    @Modifying
    @Query(value = "INSERT INTO conversa_leituras (conversa_id, usuario_id, lida_ate) VALUES (:conversa, :usuario, :quando) " +
            "ON CONFLICT (conversa_id, usuario_id) DO UPDATE SET lida_ate = GREATEST(COALESCE(conversa_leituras.lida_ate, :quando), :quando)",
            nativeQuery = true)
    void marcarLida(@Param("conversa") UUID conversa, @Param("usuario") UUID usuario, @Param("quando") LocalDateTime quando);

    /** [conversaId, fixada, arquivada, silenciada] do utilizador. */
    @Query(value = "SELECT CAST(conversa_id AS VARCHAR), fixada, arquivada, silenciada FROM conversa_leituras " +
            "WHERE usuario_id = :usuario AND conversa_id IN :conversas", nativeQuery = true)
    List<Object[]> preferencias(@Param("usuario") UUID usuario, @Param("conversas") Collection<UUID> conversas);

    @Query(value = "SELECT CAST(conversa_id AS VARCHAR) FROM conversa_leituras WHERE usuario_id = :usuario AND arquivada = true", nativeQuery = true)
    List<String> arquivadas(@Param("usuario") UUID usuario);

    @Query(value = "SELECT CAST(conversa_id AS VARCHAR) FROM conversa_leituras WHERE usuario_id = :usuario AND fixada = true", nativeQuery = true)
    List<String> fixadas(@Param("usuario") UUID usuario);

    @Modifying
    @Query(value = "INSERT INTO conversa_leituras (conversa_id, usuario_id, fixada, arquivada, silenciada) " +
            "VALUES (:conversa, :usuario, COALESCE(:fixada, false), COALESCE(:arquivada, false), COALESCE(:silenciada, false)) " +
            "ON CONFLICT (conversa_id, usuario_id) DO UPDATE SET " +
            "fixada = COALESCE(:fixada, conversa_leituras.fixada), arquivada = COALESCE(:arquivada, conversa_leituras.arquivada), " +
            "silenciada = COALESCE(:silenciada, conversa_leituras.silenciada)", nativeQuery = true)
    void guardarPreferencias(@Param("conversa") UUID conversa, @Param("usuario") UUID usuario, @Param("fixada") Boolean fixada,
                             @Param("arquivada") Boolean arquivada, @Param("silenciada") Boolean silenciada);

    /** [usuarioId, lida_ate] de todas as pessoas que já abriram a conversa. */
    @Query(value = "SELECT CAST(usuario_id AS VARCHAR), lida_ate FROM conversa_leituras WHERE conversa_id = :conversa AND lida_ate IS NOT NULL",
            nativeQuery = true)
    List<Object[]> leituras(@Param("conversa") UUID conversa);

    /** Uma mensagem nova "desarquiva" a conversa para quem a tinha arquivada (como no WhatsApp). */
    @Modifying
    @Query(value = "UPDATE conversa_leituras SET arquivada = false WHERE conversa_id = :conversa AND arquivada = true AND silenciada = false",
            nativeQuery = true)
    void desarquivar(@Param("conversa") UUID conversa);
}
