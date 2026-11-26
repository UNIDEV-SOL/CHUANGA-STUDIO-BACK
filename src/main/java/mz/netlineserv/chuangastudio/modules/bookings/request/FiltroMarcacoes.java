package mz.netlineserv.chuangastudio.modules.bookings.request;

import mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao;

import java.time.LocalDate;
import java.util.UUID;

/** Filtros da lista de marcações do painel. Todos opcionais. */
public record FiltroMarcacoes(
        StatusMarcacao status,
        String busca,
        UUID profissionalId,
        Boolean semProfissional,
        UUID clienteId,
        LocalDate dataInicio,
        LocalDate dataFim) {
}
