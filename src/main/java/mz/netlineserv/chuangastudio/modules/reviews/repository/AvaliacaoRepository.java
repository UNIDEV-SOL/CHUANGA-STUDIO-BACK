package mz.netlineserv.chuangastudio.modules.reviews.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;
import mz.netlineserv.chuangastudio.modules.reviews.domain.Avaliacao;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID>, JpaSpecificationExecutor<Avaliacao> {

    boolean existsByMarcacaoId(UUID marcacaoId);

    Optional<Avaliacao> findByTokenAndDeletadoFalse(String token);

    /** [média, nº] das avaliações publicadas (para o site e para o SEO). */
    @Query("SELECT COALESCE(AVG(a.nota), 0), COUNT(a) FROM Avaliacao a WHERE a.publicada = true AND a.deletado = false AND a.nota IS NOT NULL")
    java.util.List<Object[]> resumoPublicadas();
}
