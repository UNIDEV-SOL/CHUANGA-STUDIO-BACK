package mz.netlineserv.chuangastudio.modules.invoices.domain;

/** Como o cliente pagou a fatura na caixa (o adiantamento continua em MetodoPagamento). */
public enum FormaPagamento {
    NUMERARIO,
    MPESA,
    EMOLA,
    CARTAO,
    TRANSFERENCIA;

    public String rotulo() {
        return switch (this) {
            case NUMERARIO -> "Numerário";
            case MPESA -> "M-Pesa";
            case EMOLA -> "e-Mola";
            case CARTAO -> "Cartão";
            case TRANSFERENCIA -> "Transferência bancária";
        };
    }
}
