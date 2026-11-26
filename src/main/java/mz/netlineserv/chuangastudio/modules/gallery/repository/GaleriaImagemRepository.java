package mz.netlineserv.chuangastudio.modules.gallery.repository;

import mz.netlineserv.chuangastudio.modules.gallery.domain.GaleriaImagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GaleriaImagemRepository extends JpaRepository<GaleriaImagem, UUID> {
}
