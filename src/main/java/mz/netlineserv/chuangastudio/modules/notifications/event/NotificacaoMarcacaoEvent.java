package mz.netlineserv.chuangastudio.modules.notifications.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publicado pelos serviços; os emails só são enviados depois do commit da transação.
 * avisarEstudio: além do cliente, o estúdio também recebe um aviso (nova marcação, cancelamento ou remarcação pelo cliente).
 * anterior: data/hora antes da remarcação (só em REMARCADA).
 */
public record NotificacaoMarcacaoEvent(UUID marcacaoId, Tipo tipo, boolean avisarEstudio, LocalDateTime anterior) {

    public NotificacaoMarcacaoEvent(UUID marcacaoId, Tipo tipo, boolean avisarEstudio) {
        this(marcacaoId, tipo, avisarEstudio, null);
    }

    public enum Tipo {
        RECEBIDA,
        CONFIRMADA,
        CANCELADA,
        REMARCADA,
        LEMBRETE
    }
}
