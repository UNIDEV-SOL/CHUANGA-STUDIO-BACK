package mz.netlineserv.chuangastudio.modules.quotations.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.shared.persistence.BaseAuditEntity;

import java.math.BigDecimal;
import mz.netlineserv.chuangastudio.modules.catalog.domain.Servico;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cotacao_servicos")
public class CotacaoServico extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cotacao_id", nullable = false)
    private Cotacao cotacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @Column(name = "nome_servico", nullable = false, length = 150)
    private String nomeServico;

    @Column(name = "preco_no_momento", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoNoMomento;
}
