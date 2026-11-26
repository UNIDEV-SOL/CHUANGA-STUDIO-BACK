package mz.netlineserv.chuangastudio.modules.professionals.repository;

import mz.netlineserv.chuangastudio.modules.professionals.domain.Profissional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface ProfissionalRepository extends JpaRepository<Profissional, UUID>, JpaSpecificationExecutor<Profissional> {

    List<Profissional> findByDeletadoFalseOrderByOrdemAscNomeAsc();

    List<Profissional> findByAtivoTrueAndDeletadoFalseOrderByOrdemAscNomeAsc();

    long countByAtivoTrueAndDeletadoFalse();
}
