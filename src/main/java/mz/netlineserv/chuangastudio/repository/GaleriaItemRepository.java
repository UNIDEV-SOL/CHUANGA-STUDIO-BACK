package mz.netlineserv.chuangastudio.repository;

import mz.netlineserv.chuangastudio.domain.entity.GaleriaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface GaleriaItemRepository extends JpaRepository<GaleriaItem, UUID>, JpaSpecificationExecutor<GaleriaItem> {
}
