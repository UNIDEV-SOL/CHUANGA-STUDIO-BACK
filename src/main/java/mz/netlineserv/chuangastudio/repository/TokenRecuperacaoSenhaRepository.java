package mz.netlineserv.chuangastudio.repository;

import mz.netlineserv.chuangastudio.domain.entity.TokenRecuperacaoSenha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TokenRecuperacaoSenhaRepository extends JpaRepository<TokenRecuperacaoSenha, UUID> {

    @Query("SELECT t FROM TokenRecuperacaoSenha t JOIN FETCH t.usuario WHERE t.tokenHash = :hash AND t.deletado = false")
    Optional<TokenRecuperacaoSenha> buscarPorHash(@Param("hash") String hash);

    /** O pedido com código por confirmar mais recente da conta (um pedido novo invalida os anteriores). */
    @Query("SELECT t FROM TokenRecuperacaoSenha t JOIN FETCH t.usuario u WHERE u.email = :email AND u.deletado = false "
            + "AND t.deletado = false AND t.usadoEm IS NULL AND t.codigoHash IS NOT NULL ORDER BY t.criadoEm DESC LIMIT 1")
    Optional<TokenRecuperacaoSenha> pedidoPendente(@Param("email") String email);

    /** Um pedido novo (ou uma troca de palavra-passe) invalida os links anteriores. */
    @Modifying
    @Query("UPDATE TokenRecuperacaoSenha t SET t.usadoEm = :agora WHERE t.usuario.id = :usuarioId AND t.usadoEm IS NULL")
    void invalidarDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("agora") LocalDateTime agora);
}
