package mz.netlineserv.chuangastudio.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DiaFechadoRequest(
        @NotNull(message = "Indique a data") LocalDate data,
        @Size(max = 150, message = "O motivo não pode ter mais de 150 caracteres") String motivo) {
}
