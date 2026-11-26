package mz.netlineserv.chuangastudio.modules.loyalty.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import mz.netlineserv.chuangastudio.modules.loyalty.domain.FidelizacaoConfig;

public interface FidelizacaoConfigRepository extends JpaRepository<FidelizacaoConfig, Short> {
}
