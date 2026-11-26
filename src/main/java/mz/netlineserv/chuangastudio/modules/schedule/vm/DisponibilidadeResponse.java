package mz.netlineserv.chuangastudio.modules.schedule.vm;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Horas de início possíveis num dia. aberto = falso com motivo quando não há marcações nesse dia. */
public record DisponibilidadeResponse(LocalDate data, boolean aberto, String motivo, int duracaoMinutos,
                                      List<Horario> horarios) {

    public record Horario(LocalTime hora, boolean disponivel) {
    }
}
