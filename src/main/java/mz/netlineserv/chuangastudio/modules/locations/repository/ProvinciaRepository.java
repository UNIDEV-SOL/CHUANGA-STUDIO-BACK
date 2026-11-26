package mz.netlineserv.chuangastudio.modules.locations.repository;

import mz.netlineserv.chuangastudio.modules.locations.domain.Provincia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProvinciaRepository extends JpaRepository<Provincia, Long>, JpaSpecificationExecutor<Provincia> {
}
