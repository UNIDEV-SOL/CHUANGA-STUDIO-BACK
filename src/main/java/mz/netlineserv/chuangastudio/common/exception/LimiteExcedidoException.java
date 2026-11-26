package mz.netlineserv.chuangastudio.common.exception;

import lombok.Getter;

/** Pedido recusado por excesso de tentativas (HTTP 429); indica quando se pode tentar de novo. */
@Getter
public class LimiteExcedidoException extends RuntimeException {

    private final String codigo;
    private final long segundosParaTentarDeNovo;

    public LimiteExcedidoException(String codigo, String mensagem, long segundosParaTentarDeNovo) {
        super(mensagem);
        this.codigo = codigo;
        this.segundosParaTentarDeNovo = segundosParaTentarDeNovo;
    }
}
