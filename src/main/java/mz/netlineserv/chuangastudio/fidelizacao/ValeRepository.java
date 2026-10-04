package mz.netlineserv.chuangastudio.fidelizacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ValeRepository extends JpaRepository<Vale, UUID>, JpaSpecificationExecutor<Vale> {

    Optional<Vale> findByCodigoIgnoreCaseAndDeletadoFalse(String codigo);
}
