package mz.netlineserv.chuangastudio.common.validacao;

/** Expressões partilhadas pelas validações dos pedidos. */
public final class Formatos {

    /** Dígitos com "+" inicial opcional, espaços, hífens ou parênteses: "+258 84 123 4567", "841234567". */
    public static final String TELEFONE = "^\\+?[0-9][0-9 ()\\-]{6,24}$";
    public static final String TELEFONE_MENSAGEM =
            "Telefone inválido: use só dígitos, com \"+\" e indicativo opcionais (ex.: +258 84 123 4567).";

    private Formatos() {
    }
}
