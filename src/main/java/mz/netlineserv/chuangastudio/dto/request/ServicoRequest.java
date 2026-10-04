package mz.netlineserv.chuangastudio.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ServicoRequest {

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @NotBlank(message = "Categoria é obrigatória")
    private String categoria;

    private String descricao;

    @NotNull(message = "Preço é obrigatório")
    @Positive(message = "Preço deve ser positivo")
    private BigDecimal preco;

    @NotNull(message = "Duração é obrigatória")
    @Positive(message = "Duração deve ser positiva")
    private Integer duracaoMinutos;

    private boolean destaque;

    private boolean ativo = true;

    /** Dias até ao lembrete de manutenção (vazio = sem lembrete). */
    @jakarta.validation.constraints.Min(value = 1, message = "Os dias de manutenção devem ser pelo menos 1")
    @jakarta.validation.constraints.Max(value = 365, message = "Os dias de manutenção não podem passar de 365")
    private Integer manutencaoDias;
}
