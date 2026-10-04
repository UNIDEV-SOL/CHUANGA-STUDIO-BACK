package mz.netlineserv.chuangastudio.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import mz.netlineserv.chuangastudio.dto.HorarioDiaDto;

import java.util.List;

public record AtualizarHorarioRequest(@NotEmpty(message = "Indique o horário da semana") @Valid List<HorarioDiaDto> semana) {
}
