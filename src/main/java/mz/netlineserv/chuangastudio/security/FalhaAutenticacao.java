package mz.netlineserv.chuangastudio.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Motivo exato pelo qual o token enviado não autenticou o pedido. O filtro JWT guarda-o no
 * pedido e o {@link RespostasSeguranca} devolve-o no 401, em vez de um "não autorizado" genérico.
 */
public record FalhaAutenticacao(String codigo, String mensagem) {

    private static final String ATRIBUTO = FalhaAutenticacao.class.getName();

    public static final FalhaAutenticacao TOKEN_EXPIRADO =
            new FalhaAutenticacao("TOKEN_EXPIRADO", "A sua sessão expirou. Entre novamente.");
    public static final FalhaAutenticacao TOKEN_INVALIDO =
            new FalhaAutenticacao("TOKEN_INVALIDO", "O token de sessão é inválido (assinatura errada ou formato corrompido). Entre novamente.");
    public static final FalhaAutenticacao CONTA_INEXISTENTE =
            new FalhaAutenticacao("CONTA_INEXISTENTE", "A conta desta sessão já não existe.");
    public static final FalhaAutenticacao CONTA_DESATIVADA =
            new FalhaAutenticacao("CONTA_DESATIVADA", "A sua conta foi desativada. Contacte o estúdio.");
    public static final FalhaAutenticacao SESSAO_REVOGADA =
            new FalhaAutenticacao("SESSAO_REVOGADA", "A palavra-passe foi alterada depois de esta sessão começar. Entre novamente.");
    public static final FalhaAutenticacao CABECALHO_INVALIDO =
            new FalhaAutenticacao("TOKEN_INVALIDO", "O cabeçalho Authorization tem de ter o formato \"Bearer <token>\".");

    public void registar(HttpServletRequest request) {
        request.setAttribute(ATRIBUTO, this);
    }

    public static FalhaAutenticacao doPedido(HttpServletRequest request) {
        return request.getAttribute(ATRIBUTO) instanceof FalhaAutenticacao f ? f : null;
    }
}
