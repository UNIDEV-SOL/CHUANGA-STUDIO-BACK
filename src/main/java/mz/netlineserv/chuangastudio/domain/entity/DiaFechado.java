package mz.netlineserv.chuangastudio.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.common.BaseAuditEntity;

import java.time.LocalDate;

/** Dia sem marcações (feriado, férias, formação...). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "dias_fechados")
public class DiaFechado extends BaseAuditEntity {

    @Column(nullable = false)
    private LocalDate data;

    @Column(length = 150)
    private String motivo;
}
