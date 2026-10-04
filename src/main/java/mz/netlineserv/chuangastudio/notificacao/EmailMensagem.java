package mz.netlineserv.chuangastudio.notificacao;

import java.util.List;

/** responderPara é opcional; sem ele vale o EMAIL_RESPONDER_PARA da configuração. */
public record EmailMensagem(String para, String assunto, String html, String responderPara, List<Anexo> anexos) {

    public record Anexo(String nome, byte[] conteudo) {
    }

    public EmailMensagem(String para, String assunto, String html, String responderPara) {
        this(para, assunto, html, responderPara, List.of());
    }

    public EmailMensagem(String para, String assunto, String html) {
        this(para, assunto, html, null);
    }
}
