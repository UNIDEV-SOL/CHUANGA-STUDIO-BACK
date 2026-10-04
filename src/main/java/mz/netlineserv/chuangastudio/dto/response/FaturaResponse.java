package mz.netlineserv.chuangastudio.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import mz.netlineserv.chuangastudio.domain.entity.Fatura;
import mz.netlineserv.chuangastudio.domain.entity.FaturaItem;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class FaturaResponse {

    private UUID id;
    private String numero;
    private String status;
    private UUID marcacaoId;
    private LocalDate dataMarcacao;
    private LocalTime horaMarcacao;
    private UUID clienteId;
    private String clienteNome;
    private String clienteTelefone;
    private String clienteEmail;
    private String clienteNuit;
    private LocalDateTime dataEmissao;
    private BigDecimal subtotal;
    private BigDecimal desconto;
    private BigDecimal adiantamentoDeduzido;
    private BigDecimal descontoFidelizacao;
    private String valeCodigo;
    private BigDecimal valorVale;
    private BigDecimal totalAPagar;
    private String formaPagamento;
    private String referenciaPagamento;
    private LocalDateTime pagoEm;
    private String observacoes;
    /** Link público do PDF, para partilhar por WhatsApp. */
    private String pdfUrl;
    private List<Item> itens;

    @Getter
    @AllArgsConstructor
    public static class Item {
        private UUID servicoId;
        private String descricao;
        private Integer quantidade;
        private BigDecimal precoUnitario;
        private BigDecimal total;

        static Item deEntidade(FaturaItem item) {
            return new Item(item.getServico() != null ? item.getServico().getId() : null,
                    item.getDescricao(), item.getQuantidade(), item.getPrecoUnitario(), item.getTotal());
        }
    }

    public static FaturaResponse deEntidade(Fatura fatura, String pdfUrl) {
        Usuario cliente = fatura.getMarcacao().getCliente();
        return new FaturaResponse(
                fatura.getId(),
                fatura.getNumero(),
                fatura.getStatus().name(),
                fatura.getMarcacao().getId(),
                fatura.getMarcacao().getDataMarcacao(),
                fatura.getMarcacao().getHoraMarcacao(),
                cliente.getId(),
                cliente.getNome(),
                cliente.getTelefone(),
                cliente.getEmail(),
                fatura.getClienteNuit(),
                fatura.getDataEmissao(),
                fatura.getSubtotal(),
                fatura.getDesconto(),
                fatura.getAdiantamentoDeduzido(),
                fatura.getDescontoFidelizacao(),
                fatura.getVale() != null ? fatura.getVale().getCodigo() : null,
                fatura.getValorVale(),
                fatura.getTotalAPagar(),
                fatura.getFormaPagamento() != null ? fatura.getFormaPagamento().name() : null,
                fatura.getReferenciaPagamento(),
                fatura.getPagoEm(),
                fatura.getObservacoes(),
                pdfUrl,
                fatura.getItens().stream().map(Item::deEntidade).toList()
        );
    }
}
