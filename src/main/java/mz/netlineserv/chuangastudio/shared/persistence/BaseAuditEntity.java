package mz.netlineserv.chuangastudio.shared.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade base com auditoria (criação/atualização) e soft delete.
 * Todas as entidades de domínio devem estender esta classe, seguindo
 * o mesmo padrão usado no SPO-BACK.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @CreatedBy
    @Column(name = "criado_por", updatable = false)
    private String criadoPor;

    @LastModifiedBy
    @Column(name = "atualizado_por")
    private String atualizadoPor;

    @JsonIgnore
    @Column(name = "deletado", nullable = false)
    private boolean deletado = false;

    @JsonIgnore
    @Column(name = "deletado_em")
    private LocalDateTime deletadoEm;

    public void marcarComoDeletado() {
        this.deletado = true;
        this.deletadoEm = LocalDateTime.now();
    }
}
