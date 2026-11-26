package mz.netlineserv.chuangastudio.modules.loyalty.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;
import mz.netlineserv.chuangastudio.modules.loyalty.domain.Vale;

public interface ValeRepository extends JpaRepository<Vale, UUID>, JpaSpecificationExecutor<Vale> {

    Optional<Vale> findByCodigoIgnoreCaseAndDeletadoFalse(String codigo);
}
