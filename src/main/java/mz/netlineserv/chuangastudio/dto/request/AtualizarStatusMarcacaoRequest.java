package mz.netlineserv.chuangastudio.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao;

@Getter
@Setter
public class AtualizarStatusMarcacaoRequest {

    @NotNull(message = "Status é obrigatório")
    private StatusMarcacao status;
}
