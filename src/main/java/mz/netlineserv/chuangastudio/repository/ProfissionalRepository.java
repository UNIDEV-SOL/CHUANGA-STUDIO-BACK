package mz.netlineserv.chuangastudio.repository;

import mz.netlineserv.chuangastudio.domain.entity.Profissional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface ProfissionalRepository extends JpaRepository<Profissional, UUID>, JpaSpecificationExecutor<Profissional> {

    List<Profissional> findByDeletadoFalseOrderByOrdemAscNomeAsc();

    List<Profissional> findByAtivoTrueAndDeletadoFalseOrderByOrdemAscNomeAsc();

    long countByAtivoTrueAndDeletadoFalse();
}
