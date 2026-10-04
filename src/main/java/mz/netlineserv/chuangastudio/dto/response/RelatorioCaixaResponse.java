package mz.netlineserv.chuangastudio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Caixa de um período.
 * faturado: serviços faturados (subtotal − desconto) nas faturas emitidas no período, sem as anuladas.
 * recebido: o que entrou na caixa (total a pagar das faturas pagas no período), por forma de pagamento.
 * porReceber: todas as faturas ainda por pagar, de qualquer data.
 */
public record RelatorioCaixaResponse(
        LocalDate inicio,
        LocalDate fim,
        BigDecimal faturado,
        BigDecimal recebido,
        BigDecimal descontos,
        BigDecimal adiantamentosDeduzidos,
        BigDecimal porReceber,
        long faturasPorReceber,
        long atendimentos,
        BigDecimal ticketMedio,
        long marcacoesCanceladas,
        List<PorForma> porForma,
        List<PorDia> porDia,
        List<PorServico> topServicos) {

    public record PorForma(String forma, String rotulo, BigDecimal total, long quantidade) {
    }

    public record PorDia(LocalDate data, BigDecimal faturado, BigDecimal recebido) {
    }

    public record PorServico(String descricao, long quantidade, BigDecimal total) {
    }
}
