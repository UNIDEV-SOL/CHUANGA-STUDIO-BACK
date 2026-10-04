package mz.netlineserv.chuangastudio.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record RemarcarRequest(
        @NotNull(message = "Data é obrigatória") LocalDate data,
        @NotNull(message = "Hora é obrigatória") LocalTime hora) {
}
