package mz.netlineserv.chuangastudio.repository;

import mz.netlineserv.chuangastudio.domain.entity.HorarioFuncionamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HorarioFuncionamentoRepository extends JpaRepository<HorarioFuncionamento, UUID> {

    List<HorarioFuncionamento> findByDeletadoFalseOrderByDiaSemanaAsc();

    Optional<HorarioFuncionamento> findByDiaSemanaAndDeletadoFalse(Integer diaSemana);
}
