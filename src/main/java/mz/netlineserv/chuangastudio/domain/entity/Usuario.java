package mz.netlineserv.chuangastudio.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.common.BaseAuditEntity;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuarios")
public class Usuario extends BaseAuditEntity {

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @JsonIgnore
    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    /** Sessões (JWT) emitidas antes disto são recusadas. Nula = nunca foi alterada. */
    @JsonIgnore
    @Column(name = "senha_alterada_em")
    private LocalDateTime senhaAlteradaEm;

    @Column(length = 30)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PapelUsuario papel = PapelUsuario.CLIENTE;

    @Column(name = "conta_ativa", nullable = false)
    private boolean contaAtiva = true;

    @Column(name = "login_google", nullable = false)
    private boolean loginGoogle = false;

    /** Ficha da cliente: só a equipa vê (alergias, formato/cor preferidos...). */
    @JsonIgnore
    @Column(name = "notas_internas", columnDefinition = "TEXT")
    private String notasInternas;

    /** Morada (opcional). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provincia_id")
    private Provincia provincia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distrito_id")
    private Distrito distrito;
}
