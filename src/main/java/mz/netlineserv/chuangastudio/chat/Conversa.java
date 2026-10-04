package mz.netlineserv.chuangastudio.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.common.BaseAuditEntity;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;

import java.time.LocalDateTime;

/**
 * PRIVADA: uma por cliente, falada com toda a equipa do estúdio.
 * DIRETA: entre duas pessoas (ex. duas clientes), só elas a veem.
 * GRUPO: com membros e administradoras; os de sistema (COMUNIDADE, AVISOS) não se apagam,
 * os de serviço (servico) recebem automaticamente quem marca esse serviço.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "conversas")
public class Conversa extends BaseAuditEntity {

    public enum Tipo { PRIVADA, DIRETA, GRUPO }

    public enum Sistema { COMUNIDADE, AVISOS }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @Column(nullable = false, length = 120)
    private String titulo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Usuario cliente;

    @Column(name = "ultima_mensagem", length = 300)
    private String ultimaMensagem;

    @Column(name = "ultima_mensagem_em")
    private LocalDateTime ultimaMensagemEm;

    /** Só as administradoras enviam mensagens. */
    @Column(nullable = false)
    private boolean bloqueada = false;

    @Column(length = 500)
    private String descricao;

    @Column(name = "foto_anexo_id")
    private java.util.UUID fotoAnexoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_id")
    private mz.netlineserv.chuangastudio.domain.entity.Servico servico;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Sistema sistema;

    /** Aparece na comunidade. */
    @Column(nullable = false)
    private boolean comunidade = false;

    /** Qualquer cliente pode entrar pela comunidade. */
    @Column(nullable = false)
    private boolean aberto = false;

    /** Só as administradoras mudam nome, descrição e foto. */
    @Column(name = "so_admins_editam", nullable = false)
    private boolean soAdminsEditam = true;

    @Column(name = "mensagem_fixada_id")
    private java.util.UUID mensagemFixadaId;
}
