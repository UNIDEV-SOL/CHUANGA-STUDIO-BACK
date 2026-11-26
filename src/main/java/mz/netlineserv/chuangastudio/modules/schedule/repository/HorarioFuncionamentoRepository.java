package mz.netlineserv.chuangastudio.modules.schedule.repository;

import mz.netlineserv.chuangastudio.modules.schedule.domain.HorarioFuncionamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HorarioFuncionamentoRepository extends JpaRepository<HorarioFuncionamento, UUID> {

    List<HorarioFuncionamento> findByDeletadoFalseOrderByDiaSemanaAsc();

    Optional<HorarioFuncionamento> findByDiaSemanaAndDeletadoFalse(Integer diaSemana);
}
