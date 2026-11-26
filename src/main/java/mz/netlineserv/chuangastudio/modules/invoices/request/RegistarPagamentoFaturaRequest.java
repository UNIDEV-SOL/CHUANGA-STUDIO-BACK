package mz.netlineserv.chuangastudio.modules.invoices.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import mz.netlineserv.chuangastudio.modules.invoices.domain.FormaPagamento;

@Getter
@Setter
public class RegistarPagamentoFaturaRequest {

    @NotNull(message = "Forma de pagamento é obrigatória")
    private FormaPagamento formaPagamento;

    @Size(max = 60, message = "A referência não pode ter mais de 60 caracteres")
    private String referenciaPagamento;
}
