package mz.netlineserv.chuangastudio.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.common.BaseAuditEntity;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "servicos")
public class Servico extends BaseAuditEntity {

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 80)
    private String categoria;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(name = "duracao_minutos", nullable = false)
    private Integer duracaoMinutos;

    @Column(nullable = false)
    private boolean destaque = false;

    @Column(nullable = false)
    private boolean ativo = true;

    /** Lembrete de manutenção: X dias depois do serviço (nulo = sem lembrete). */
    @Column(name = "manutencao_dias")
    private Integer manutencaoDias;
}
