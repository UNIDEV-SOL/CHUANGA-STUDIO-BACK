package mz.netlineserv.chuangastudio.repository;

import mz.netlineserv.chuangastudio.domain.entity.Servico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServicoRepository extends JpaRepository<Servico, UUID> {

    List<Servico> findByDeletadoFalseOrderByCategoriaAscNomeAsc();

    List<Servico> findByAtivoTrueAndDeletadoFalseOrderByCategoriaAscNomeAsc();

    List<Servico> findByDestaqueTrueAndAtivoTrueAndDeletadoFalse();
}
