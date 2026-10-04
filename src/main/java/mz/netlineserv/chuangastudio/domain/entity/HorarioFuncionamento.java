package mz.netlineserv.chuangastudio.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.common.BaseAuditEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalTime;

/** Horário de um dia da semana. diaSemana segue java.time.DayOfWeek (1 = segunda ... 7 = domingo). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "horario_funcionamento")
public class HorarioFuncionamento extends BaseAuditEntity {

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "dia_semana", nullable = false, unique = true)
    private Integer diaSemana;

    @Column(nullable = false)
    private boolean aberto = true;

    private LocalTime abre;

    private LocalTime fecha;

    @Column(name = "pausa_inicio")
    private LocalTime pausaInicio;

    @Column(name = "pausa_fim")
    private LocalTime pausaFim;
}
