package mz.netlineserv.chuangastudio.modules.quotations.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.shared.persistence.BaseAuditEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cotacoes")
public class Cotacao extends BaseAuditEntity {

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 30)
    private String telefone;

    @Column(length = 180)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "total_estimado", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEstimado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusCotacao status = StatusCotacao.NOVO;

    @OneToMany(mappedBy = "cotacao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CotacaoServico> servicos = new ArrayList<>();

    public void adicionarServico(CotacaoServico item) {
        item.setCotacao(this);
        this.servicos.add(item);
    }
}
