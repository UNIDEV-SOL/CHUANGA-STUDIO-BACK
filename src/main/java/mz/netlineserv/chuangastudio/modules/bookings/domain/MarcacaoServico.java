package mz.netlineserv.chuangastudio.modules.bookings.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.shared.persistence.BaseAuditEntity;

import java.math.BigDecimal;
import mz.netlineserv.chuangastudio.modules.catalog.domain.Servico;

/**
 * Item de serviço dentro de uma marcação. Guarda o preço e nome no momento
 * da reserva, para não ser afetado caso o serviço seja alterado depois.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "marcacao_servicos")
public class MarcacaoServico extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marcacao_id", nullable = false)
    private Marcacao marcacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @Column(name = "nome_servico", nullable = false, length = 150)
    private String nomeServico;

    @Column(name = "preco_no_momento", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoNoMomento;
}
