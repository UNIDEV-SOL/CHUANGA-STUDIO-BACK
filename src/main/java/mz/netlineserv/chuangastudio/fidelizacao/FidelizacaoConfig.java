package mz.netlineserv.chuangastudio.fidelizacao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Regra do cartão de fidelização (linha única, id = 1). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fidelizacao_config")
public class FidelizacaoConfig {

    @Id
    private Short id = 1;

    @Column(nullable = false)
    private boolean ativo = true;

    /** A cada N visitas (atendimentos faturados) ganha-se uma recompensa. */
    @Column(name = "visitas_por_recompensa", nullable = false)
    private Integer visitasPorRecompensa = 10;

    @Column(name = "valor_recompensa", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorRecompensa = new BigDecimal("500.00");

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
