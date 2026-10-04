package mz.netlineserv.chuangastudio.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Classificador fixo (dados do seed db/seed/provincias_distritos.sql). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "distritos")
public class Distrito {

    @Id
    private Long id;

    @Column(nullable = false, length = 10)
    private String codigo;

    @Column(nullable = false, length = 80)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provincia_id", nullable = false)
    private Provincia provincia;
}
