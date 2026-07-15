package mz.netlineserv.chuangastudio.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.common.BaseAuditEntity;
import mz.netlineserv.chuangastudio.domain.enums.MetodoPagamento;
import mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "marcacoes")
public class Marcacao extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @Column(name = "data_marcacao", nullable = false)
    private LocalDate dataMarcacao;

    @Column(name = "hora_marcacao", nullable = false)
    private LocalTime horaMarcacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusMarcacao status = StatusMarcacao.AGUARDA_CONFIRMACAO;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "valor_adiantamento", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorAdiantamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pagamento", length = 20)
    private MetodoPagamento metodoPagamento;

    @Column(name = "referencia_pagamento", length = 60)
    private String referenciaPagamento;

    @Column(name = "adiantamento_pago", nullable = false)
    private boolean adiantamentoPago = false;

    @OneToMany(mappedBy = "marcacao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MarcacaoServico> servicos = new ArrayList<>();

    public void adicionarServico(MarcacaoServico item) {
        item.setMarcacao(this);
        this.servicos.add(item);
    }
}
