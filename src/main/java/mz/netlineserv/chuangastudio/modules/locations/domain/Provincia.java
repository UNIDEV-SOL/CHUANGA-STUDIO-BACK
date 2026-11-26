package mz.netlineserv.chuangastudio.modules.locations.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Classificador fixo (dados do seed db/seed/provincias_distritos.sql). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "provincias")
public class Provincia {

    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String codigo;

    @Column(nullable = false, length = 80)
    private String nome;

    @Column(nullable = false, length = 5)
    private String sigla;

    @Column(name = "zona_regional_id", nullable = false)
    private Short zonaRegionalId;
}
