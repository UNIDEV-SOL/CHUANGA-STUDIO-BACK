package mz.netlineserv.chuangastudio.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** Um dia da semana no horário de funcionamento (1 = segunda ... 7 = domingo). */
public record HorarioDiaDto(
        @NotNull @Min(1) @Max(7) Integer diaSemana,
        boolean aberto,
        LocalTime abre,
        LocalTime fecha,
        LocalTime pausaInicio,
        LocalTime pausaFim) {
}
