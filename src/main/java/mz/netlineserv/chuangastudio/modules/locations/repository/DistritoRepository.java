package mz.netlineserv.chuangastudio.modules.locations.repository;

import mz.netlineserv.chuangastudio.modules.locations.domain.Distrito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DistritoRepository extends JpaRepository<Distrito, Long>, JpaSpecificationExecutor<Distrito> {
}
