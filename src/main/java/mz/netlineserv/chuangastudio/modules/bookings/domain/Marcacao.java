package mz.netlineserv.chuangastudio.modules.bookings.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.shared.persistence.BaseAuditEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import mz.netlineserv.chuangastudio.modules.professionals.domain.Profissional;
import mz.netlineserv.chuangastudio.modules.users.domain.Usuario;

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

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    /** Preenchido quando o lembrete da véspera é enviado; volta a nulo se a marcação for remarcada. */
    @Column(name = "lembrete_enviado_em")
    private LocalDateTime lembreteEnviadoEm;

    @Column(name = "lembrete_manutencao_em")
    private LocalDateTime lembreteManutencaoEm;

    /** Quem faz o atendimento. Nulo em marcações antigas ou quando o estúdio não tem equipa registada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id")
    private Profissional profissional;

    @OneToMany(mappedBy = "marcacao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MarcacaoServico> servicos = new ArrayList<>();

    public void adicionarServico(MarcacaoServico item) {
        item.setMarcacao(this);
        this.servicos.add(item);
    }
}
