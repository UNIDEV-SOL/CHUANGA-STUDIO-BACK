package mz.netlineserv.chuangastudio.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import mz.netlineserv.chuangastudio.domain.enums.MetodoPagamento;


import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CriarMarcacaoRequest {

    @NotEmpty(message = "Selecione pelo menos um serviço")
    private List<UUID> servicosIds;

    @NotNull(message = "Data é obrigatória")
    private LocalDate data;

    @NotNull(message = "Hora é obrigatória")
    private LocalTime hora;

    // Dados do cliente (usados quando ainda não está autenticado)
    private String nome;

    @Email(message = "Email inválido")
    private String email;

    private String telefone;

    private String senha; // usada para criar conta, se o cliente não estiver logado

    @NotNull(message = "Método de pagamento é obrigatório")
    private MetodoPagamento metodoPagamento;

    @NotBlank(message = "Referência do pagamento é obrigatória")
    private String referenciaPagamento;
}
