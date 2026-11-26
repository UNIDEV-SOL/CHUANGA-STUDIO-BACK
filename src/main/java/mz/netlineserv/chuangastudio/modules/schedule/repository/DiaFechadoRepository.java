package mz.netlineserv.chuangastudio.modules.schedule.repository;

import mz.netlineserv.chuangastudio.modules.schedule.domain.DiaFechado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiaFechadoRepository extends JpaRepository<DiaFechado, UUID> {

    Optional<DiaFechado> findByDataAndDeletadoFalse(LocalDate data);

    List<DiaFechado> findByDataGreaterThanEqualAndDeletadoFalseOrderByDataAsc(LocalDate data);
}
