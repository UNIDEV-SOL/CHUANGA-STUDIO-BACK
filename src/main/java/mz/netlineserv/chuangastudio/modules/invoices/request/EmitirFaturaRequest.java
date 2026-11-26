package mz.netlineserv.chuangastudio.modules.invoices.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import mz.netlineserv.chuangastudio.modules.invoices.domain.FormaPagamento;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Fecho de conta na caixa. Os itens são os serviços realmente feitos (podem
 * diferir da reserva). formaPagamento preenchida = o cliente já pagou ali mesmo.
 */
@Getter
@Setter
public class EmitirFaturaRequest {

    @NotEmpty(message = "Indique pelo menos um serviço realizado")
    @Size(max = 50, message = "Uma fatura pode ter até 50 linhas")
    @Valid
    private List<Item> itens;

    @DecimalMin(value = "0.00", message = "O desconto não pode ser negativo")
    private BigDecimal desconto;

    @Size(max = 500, message = "As observações não podem ter mais de 500 caracteres")
    private String observacoes;

    private FormaPagamento formaPagamento;

    @Pattern(regexp = "^$|^\\d{9}$", message = "O NUIT tem 9 dígitos")
    private String clienteNuit;

    /** Usar uma recompensa do cartão de fidelização da cliente. */
    private boolean aplicarFidelizacao;

    /** Código de um vale-oferta para pagar (total ou parcialmente). */
    @Size(max = 20, message = "Código de vale inválido")
    private String codigoVale;

    @Size(max = 60, message = "A referência não pode ter mais de 60 caracteres")
    private String referenciaPagamento;

    /** servicoId do catálogo, ou descricao + precoUnitario para um extra. */
    @Getter
    @Setter
    public static class Item {

        private UUID servicoId;

        @Size(max = 150, message = "A descrição não pode ter mais de 150 caracteres")
        private String descricao;

        @DecimalMin(value = "0.00", message = "O preço não pode ser negativo")
        private BigDecimal precoUnitario;

        @Min(value = 1, message = "A quantidade mínima é 1")
        @Max(value = 100, message = "A quantidade máxima por linha é 100")
        private Integer quantidade;
    }
}
