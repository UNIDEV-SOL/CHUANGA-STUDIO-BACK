package mz.netlineserv.chuangastudio.modules.notifications.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.shared.persistence.BaseAuditEntity;

import java.util.UUID;

/** Aviso para a equipa no painel. link = rota do painel a abrir ao clicar. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notificacoes")
public class Notificacao extends BaseAuditEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoNotificacao tipo;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(length = 400)
    private String mensagem;

    @Column(length = 200)
    private String link;

    @Column(name = "referencia_id")
    private UUID referenciaId;
}
