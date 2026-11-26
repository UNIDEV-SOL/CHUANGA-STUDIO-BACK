package mz.netlineserv.chuangastudio.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.common.BaseAuditEntity;

import java.time.LocalDateTime;

/**
 * Pedido de nova palavra-passe por código OTP. Só se guardam hashes: o do código enviado por email e,
 * depois de o código ser confirmado, o do token curto que autoriza escolher a nova palavra-passe.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tokens_recuperacao_senha")
public class TokenRecuperacaoSenha extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "usado_em")
    private LocalDateTime usadoEm;

    /** Hash do código de 6 dígitos; fica nulo depois de confirmado (não serve uma segunda vez). */
    @Column(name = "codigo_hash", length = 64)
    private String codigoHash;

    /** Códigos errados já tentados neste pedido. */
    @Column(name = "tentativas", nullable = false)
    private int tentativas;
}
