package mz.netlineserv.chuangastudio.modules.invoices.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Reenvio manual (ex.: o cliente diz que não recebeu). Sem email = o da conta do cliente. */
@Getter
@Setter
public class ReenviarFaturaRequest {

    @Email(message = "Email inválido")
    @Size(max = 180, message = "Email demasiado longo")
    private String email;
}
