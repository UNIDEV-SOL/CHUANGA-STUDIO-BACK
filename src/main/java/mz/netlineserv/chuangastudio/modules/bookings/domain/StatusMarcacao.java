package mz.netlineserv.chuangastudio.modules.bookings.domain;

import java.util.Set;

public enum StatusMarcacao {
    AGUARDA_CONFIRMACAO,
    CONFIRMADA,
    CONCLUIDA,
    CANCELADA,
    /** A cliente faltou sem avisar: o adiantamento fica retido e conta no histórico de faltas. */
    NAO_COMPARECEU;

    /** CONCLUIDA, CANCELADA e NAO_COMPARECEU são estados finais. */
    public boolean podeTransitarPara(StatusMarcacao novo) {
        return switch (this) {
            case AGUARDA_CONFIRMACAO -> Set.of(CONFIRMADA, CANCELADA, NAO_COMPARECEU).contains(novo);
            case CONFIRMADA -> Set.of(CONCLUIDA, CANCELADA, NAO_COMPARECEU).contains(novo);
            case CONCLUIDA, CANCELADA, NAO_COMPARECEU -> false;
        };
    }

    public boolean isAtiva() {
        return this == AGUARDA_CONFIRMACAO || this == CONFIRMADA;
    }
}
