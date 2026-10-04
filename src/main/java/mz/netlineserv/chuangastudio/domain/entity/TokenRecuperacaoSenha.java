package mz.netlineserv.chuangastudio.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.common.BaseAuditEntity;

import java.time.LocalDateTime;

/** Pedido de nova palavra-passe. Só o hash SHA-256 do token é guardado; o token vai no link do email. */
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
}
