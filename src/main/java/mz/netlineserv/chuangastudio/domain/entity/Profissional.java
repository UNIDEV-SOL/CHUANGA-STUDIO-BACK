package mz.netlineserv.chuangastudio.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.common.BaseAuditEntity;

/** Técnica do estúdio. Só as ativas contam para a capacidade e aparecem na reserva. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "profissionais")
public class Profissional extends BaseAuditEntity {

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 30)
    private String telefone;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private Integer ordem = 0;
}
