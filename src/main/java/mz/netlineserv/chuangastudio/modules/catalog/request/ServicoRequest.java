package mz.netlineserv.chuangastudio.modules.catalog.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ServicoRequest {

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 150, message = "O nome pode ter até 150 caracteres")
    private String nome;

    @NotBlank(message = "Categoria é obrigatória")
    @Size(max = 80, message = "A categoria pode ter até 80 caracteres")
    private String categoria;

    @Size(max = 2000, message = "A descrição pode ter até 2000 caracteres")
    private String descricao;

    @NotNull(message = "Preço é obrigatório")
    @Positive(message = "Preço deve ser positivo")
    @Digits(integer = 8, fraction = 2, message = "Preço inválido: até 8 dígitos e 2 casas decimais")
    private BigDecimal preco;

    @NotNull(message = "Duração é obrigatória")
    @Positive(message = "Duração deve ser positiva")
    @Max(value = 720, message = "A duração não pode passar de 720 minutos (12 horas)")
    private Integer duracaoMinutos;

    private boolean destaque;

    private boolean ativo = true;

    /** Dias até ao lembrete de manutenção (vazio = sem lembrete). */
    @jakarta.validation.constraints.Min(value = 1, message = "Os dias de manutenção devem ser pelo menos 1")
    @jakarta.validation.constraints.Max(value = 365, message = "Os dias de manutenção não podem passar de 365")
    private Integer manutencaoDias;
}
