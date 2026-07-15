package mz.netlineserv.chuangastudio.repository;

import mz.netlineserv.chuangastudio.domain.entity.Cotacao;
import mz.netlineserv.chuangastudio.domain.enums.StatusCotacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CotacaoRepository extends JpaRepository<Cotacao, UUID> {

    List<Cotacao> findByDeletadoFalseOrderByCriadoEmDesc();

    List<Cotacao> findByStatusAndDeletadoFalseOrderByCriadoEmDesc(StatusCotacao status);

    long countByDeletadoFalse();
}
