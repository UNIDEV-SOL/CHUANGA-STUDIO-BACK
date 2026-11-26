package mz.netlineserv.chuangastudio.modules.bookings.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mz.netlineserv.chuangastudio.shared.validators.Formatos;
import mz.netlineserv.chuangastudio.shared.validators.SenhaValida;
import lombok.Getter;
import lombok.Setter;
import mz.netlineserv.chuangastudio.modules.bookings.domain.MetodoPagamento;


import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CriarMarcacaoRequest {

    @NotEmpty(message = "Selecione pelo menos um serviço")
    @Size(max = 10, message = "Pode reservar até 10 serviços de cada vez")
    private List<UUID> servicosIds;

    @NotNull(message = "Data é obrigatória")
    private LocalDate data;

    @NotNull(message = "Hora é obrigatória")
    private LocalTime hora;

    // Dados do cliente (usados quando ainda não está autenticado)
    @Size(max = 150, message = "O nome pode ter até 150 caracteres")
    private String nome;

    @Email(message = "Email inválido")
    @Size(max = 180, message = "O email pode ter até 180 caracteres")
    private String email;

    @Pattern(regexp = Formatos.TELEFONE, message = Formatos.TELEFONE_MENSAGEM)
    private String telefone;

    @SenhaValida
    private String senha; // usada para criar conta, se o cliente não estiver logado

    @NotNull(message = "Método de pagamento é obrigatório")
    private MetodoPagamento metodoPagamento;

    @NotBlank(message = "Referência do pagamento é obrigatória")
    @Size(max = 60, message = "A referência do pagamento pode ter até 60 caracteres")
    private String referenciaPagamento;

    @Size(max = 500, message = "As observações não podem ter mais de 500 caracteres")
    private String observacoes;

    /** Profissional preferida; nula = a primeira livre. */
    private UUID profissionalId;

    /** Morada da cliente nova (opcional). */
    private Long provinciaId;

    private Long distritoId;
}
