package mz.netlineserv.chuangastudio.modules.bookings.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao;

@Getter
@Setter
public class AtualizarStatusMarcacaoRequest {

    @NotNull(message = "Status é obrigatório")
    private StatusMarcacao status;
}
