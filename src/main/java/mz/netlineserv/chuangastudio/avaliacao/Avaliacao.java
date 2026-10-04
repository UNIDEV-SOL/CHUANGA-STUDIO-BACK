package mz.netlineserv.chuangastudio.avaliacao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.common.BaseAuditEntity;
import mz.netlineserv.chuangastudio.domain.entity.Marcacao;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;

import java.time.LocalDateTime;

/** Pedida por email depois do atendimento (link com token); a equipa escolhe as que aparecem no site. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "avaliacoes")
public class Avaliacao extends BaseAuditEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marcacao_id", nullable = false, unique = true)
    private Marcacao marcacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    private Short nota;

    @Column(length = 1000)
    private String comentario;

    @Column(name = "respondida_em")
    private LocalDateTime respondidaEm;

    @Column(nullable = false)
    private boolean publicada = false;
}
