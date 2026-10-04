package mz.netlineserv.chuangastudio.repository;

import mz.netlineserv.chuangastudio.domain.entity.GaleriaImagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GaleriaImagemRepository extends JpaRepository<GaleriaImagem, UUID> {
}
