package mz.netlineserv.chuangastudio.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.common.BaseAuditEntity;

import java.math.BigDecimal;

/** Linha da fatura. servico é nulo para extras escritos à mão na caixa. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fatura_itens")
public class FaturaItem extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fatura_id", nullable = false)
    private Fatura fatura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    @Column(nullable = false, length = 150)
    private String descricao;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoUnitario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;
}
