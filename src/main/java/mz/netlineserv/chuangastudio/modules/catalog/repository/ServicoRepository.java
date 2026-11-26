package mz.netlineserv.chuangastudio.modules.catalog.repository;

import mz.netlineserv.chuangastudio.modules.catalog.domain.Servico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface ServicoRepository extends JpaRepository<Servico, UUID>, JpaSpecificationExecutor<Servico> {

    List<Servico> findByDeletadoFalseOrderByCategoriaAscNomeAsc();

    List<Servico> findByAtivoTrueAndDeletadoFalseOrderByCategoriaAscNomeAsc();

    List<Servico> findByDestaqueTrueAndAtivoTrueAndDeletadoFalse();
}
