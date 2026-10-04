package mz.netlineserv.chuangastudio.dto.response;

import mz.netlineserv.chuangastudio.dto.HorarioDiaDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ConfiguracaoHorarioResponse(List<HorarioDiaDto> semana, List<DiaFechadoItem> diasFechados,
                                          int intervaloMinutos, int diasAntecedenciaMaxima) {

    public record DiaFechadoItem(UUID id, LocalDate data, String motivo) {
    }
}
