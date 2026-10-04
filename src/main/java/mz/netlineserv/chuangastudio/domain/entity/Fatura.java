package mz.netlineserv.chuangastudio.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.common.BaseAuditEntity;
import mz.netlineserv.chuangastudio.domain.enums.FormaPagamento;
import mz.netlineserv.chuangastudio.domain.enums.StatusFatura;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Conta final de uma marcação: os serviços realmente feitos, menos desconto
 * e adiantamento já pago. Emitida na caixa quando o atendimento termina.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "faturas")
public class Fatura extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marcacao_id", nullable = false)
    private Marcacao marcacao;

    @Column(nullable = false, unique = true, length = 20)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusFatura status = StatusFatura.PENDENTE;

    @Column(name = "data_emissao", nullable = false)
    private LocalDateTime dataEmissao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(name = "adiantamento_deduzido", nullable = false, precision = 12, scale = 2)
    private BigDecimal adiantamentoDeduzido = BigDecimal.ZERO;

    @Column(name = "total_a_pagar", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAPagar;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 20)
    private FormaPagamento formaPagamento;

    @Column(name = "referencia_pagamento", length = 60)
    private String referenciaPagamento;

    @Column(name = "pago_em")
    private LocalDateTime pagoEm;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    /** Opcional: ISPC (2026) pede o NUIT dos dois intervenientes quando o cliente o tem. */
    @Column(name = "cliente_nuit", length = 20)
    private String clienteNuit;

    /** Recompensa do cartão de fidelização aplicada (desconto). */
    @Column(name = "desconto_fidelizacao", nullable = false, precision = 12, scale = 2)
    private BigDecimal descontoFidelizacao = BigDecimal.ZERO;

    /** Vale-oferta usado como forma de pagamento (parcial ou total). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vale_id")
    private mz.netlineserv.chuangastudio.fidelizacao.Vale vale;

    @Column(name = "valor_vale", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorVale = BigDecimal.ZERO;

    @Column(name = "token_publico", nullable = false, unique = true, length = 64)
    private String tokenPublico;

    @OneToMany(mappedBy = "fatura", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("criadoEm ASC")
    private List<FaturaItem> itens = new ArrayList<>();

    public void adicionarItem(FaturaItem item) {
        item.setFatura(this);
        this.itens.add(item);
    }
}
