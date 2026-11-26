package mz.netlineserv.chuangastudio.modules.notifications.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/** Uma notificação lida por um utilizador (só serve para filtrar; as escritas são em SQL no repositório). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notificacao_leituras")
public class NotificacaoLeitura {

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Chave implements Serializable {
        @Column(name = "notificacao_id")
        private UUID notificacaoId;

        @Column(name = "usuario_id")
        private UUID usuarioId;

        @Override
        public boolean equals(Object o) {
            return o instanceof Chave c && c.notificacaoId.equals(notificacaoId) && c.usuarioId.equals(usuarioId);
        }

        @Override
        public int hashCode() {
            return notificacaoId.hashCode() * 31 + usuarioId.hashCode();
        }
    }

    @EmbeddedId
    private Chave id;

    @Column(name = "lida_em", nullable = false)
    private LocalDateTime lidaEm;
}
