package mz.netlineserv.chuangastudio.repository;

import mz.netlineserv.chuangastudio.domain.entity.Distrito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DistritoRepository extends JpaRepository<Distrito, Long>, JpaSpecificationExecutor<Distrito> {
}
