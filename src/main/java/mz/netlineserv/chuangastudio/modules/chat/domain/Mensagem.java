package mz.netlineserv.chuangastudio.modules.chat.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.shared.persistence.BaseAuditEntity;
import mz.netlineserv.chuangastudio.modules.users.domain.Usuario;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mensagens")
public class Mensagem extends BaseAuditEntity {

    public enum Tipo { TEXTO, IMAGEM, AUDIO, DOCUMENTO, SISTEMA }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversa_id", nullable = false)
    private Conversa conversa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "remetente_id", nullable = false)
    private Usuario remetente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Tipo tipo = Tipo.TEXTO;

    @Column(length = 2000)
    private String conteudo;

    @Column(name = "anexo_id")
    private UUID anexoId;

    @Column(name = "resposta_a")
    private UUID respostaA;

    @Column(name = "editada_em")
    private LocalDateTime editadaEm;

    /** Apagada fica como "Mensagem apagada" (o histórico não salta). */
    @Column(nullable = false)
    private boolean apagada = false;

    @Column(nullable = false)
    private boolean encaminhada = false;

    @Column(name = "duracao_segundos")
    private Integer duracaoSegundos;
}
