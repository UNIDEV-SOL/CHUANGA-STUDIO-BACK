package mz.netlineserv.chuangastudio.modules.loyalty.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.shared.persistence.BaseAuditEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Vale-oferta com saldo: pode ser usado em vários atendimentos até esgotar. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vales")
public class Vale extends BaseAuditEntity {

    public enum Estado { ATIVO, ESGOTADO, ANULADO }

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(name = "valor_inicial", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorInicial;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal saldo;

    @Column(name = "comprador_nome", length = 150)
    private String compradorNome;

    @Column(name = "comprador_contacto", length = 150)
    private String compradorContacto;

    @Column(name = "beneficiario_nome", length = 150)
    private String beneficiarioNome;

    @Column(name = "beneficiario_email", length = 180)
    private String beneficiarioEmail;

    @Column(length = 400)
    private String mensagem;

    @Column(name = "valido_ate")
    private LocalDate validoAte;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.ATIVO;
}
