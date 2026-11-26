package mz.netlineserv.chuangastudio.modules.notifications.event;

import java.util.UUID;

/**
 * Publicado ao emitir, pagar ou reenviar uma fatura. O conteúdo (fatura ou recibo)
 * depende do estado da fatura no momento do envio.
 *
 * emailDestino: substitui o email do cliente (reenvio manual para outro endereço); nulo = o da conta.
 * avisarEstudio: falso nos reenvios manuais, o estúdio já tem a cópia.
 */
public record NotificacaoFaturaEvent(UUID faturaId, String emailDestino, boolean avisarEstudio) {

    public NotificacaoFaturaEvent(UUID faturaId) {
        this(faturaId, null, true);
    }
}
