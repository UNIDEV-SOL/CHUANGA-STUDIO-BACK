package mz.netlineserv.chuangastudio.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversaMembroRepository extends JpaRepository<ConversaMembro, ConversaMembro.Chave> {

    Optional<ConversaMembro> findByConversaAndUsuario(UUID conversa, UUID usuario);

    List<ConversaMembro> findByConversa(UUID conversa);

    @Query("SELECT m FROM ConversaMembro m JOIN FETCH m.pessoa p WHERE m.conversa = :conversa AND m.saiuEm IS NULL " +
            "AND p.deletado = false ORDER BY m.admin DESC, p.nome ASC")
    List<ConversaMembro> ativos(@Param("conversa") UUID conversa);

    @Query("SELECT m.conversa, COUNT(m) FROM ConversaMembro m WHERE m.conversa IN :conversas AND m.saiuEm IS NULL GROUP BY m.conversa")
    List<Object[]> contagens(@Param("conversas") Collection<UUID> conversas);

    @Query("SELECT m.conversa FROM ConversaMembro m WHERE m.usuario = :usuario AND m.saiuEm IS NULL")
    List<UUID> conversasDe(@Param("usuario") UUID usuario);

    /** Junta sem mexer em quem já lá está ou saiu (devolve 1 se entrou agora). */
    @Modifying
    @Query(value = "INSERT INTO conversa_membros (conversa_id, usuario_id, admin, entrou_em) VALUES (:conversa, :usuario, :admin, NOW()) " +
            "ON CONFLICT DO NOTHING", nativeQuery = true)
    int juntarSeNovo(@Param("conversa") UUID conversa, @Param("usuario") UUID usuario, @Param("admin") boolean admin);

    /** Conversa DIRETA entre duas pessoas, se já existir. */
    @Query(value = "SELECT CAST(c.id AS VARCHAR) FROM conversas c " +
            "JOIN conversa_membros a ON a.conversa_id = c.id AND a.usuario_id = :a " +
            "JOIN conversa_membros b ON b.conversa_id = c.id AND b.usuario_id = :b " +
            "WHERE c.tipo = 'DIRETA' AND c.deletado = false LIMIT 1", nativeQuery = true)
    Optional<String> direta(@Param("a") UUID a, @Param("b") UUID b);

    /** Grupos em que as duas estão. */
    @Query(value = "SELECT CAST(c.id AS VARCHAR), c.titulo FROM conversas c " +
            "JOIN conversa_membros a ON a.conversa_id = c.id AND a.usuario_id = :a AND a.saiu_em IS NULL " +
            "JOIN conversa_membros b ON b.conversa_id = c.id AND b.usuario_id = :b AND b.saiu_em IS NULL " +
            "WHERE c.tipo = 'GRUPO' AND c.deletado = false ORDER BY c.titulo", nativeQuery = true)
    List<Object[]> gruposEmComum(@Param("a") UUID a, @Param("b") UUID b);
}
