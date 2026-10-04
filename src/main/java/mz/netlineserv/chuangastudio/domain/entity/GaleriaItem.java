package mz.netlineserv.chuangastudio.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.common.BaseAuditEntity;
import mz.netlineserv.chuangastudio.domain.enums.EstadoGaleria;

import java.util.UUID;

/** Trabalho publicado na galeria do site. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "galeria_itens")
public class GaleriaItem extends BaseAuditEntity {

    @Column(name = "imagem_id", nullable = false)
    private UUID imagemId;

    @Column(length = 120)
    private String titulo;

    @Column(length = 400)
    private String descricao;

    @Column(length = 80)
    private String categoria;

    private Integer largura;

    private Integer altura;

    @Column(nullable = false)
    private Integer ordem = 0;

    @Column(nullable = false)
    private boolean publicado = true;

    @Column(nullable = false)
    private boolean destaque = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoGaleria estado = EstadoGaleria.APROVADA;

    /** Cliente que sugeriu a foto (nulo se foi a equipa a carregar). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sugerido_por")
    private Usuario sugeridoPor;

    @Column(name = "motivo_rejeicao", length = 200)
    private String motivoRejeicao;
}
