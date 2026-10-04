package mz.netlineserv.chuangastudio.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/** Pessoa num grupo ou conversa direta. saiuEm preenchido = já não está (e não é readicionada sozinha). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "conversa_membros")
@IdClass(ConversaMembro.Chave.class)
public class ConversaMembro {

    public record Chave(UUID conversa, UUID usuario) implements Serializable {
        public Chave() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "conversa_id")
    private UUID conversa;

    @Id
    @Column(name = "usuario_id")
    private UUID usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", insertable = false, updatable = false)
    private Usuario pessoa;

    @Column(nullable = false)
    private boolean admin;

    @Column(name = "entrou_em", nullable = false)
    private LocalDateTime entrouEm = LocalDateTime.now();

    @Column(name = "saiu_em")
    private LocalDateTime saiuEm;

    public boolean isAtivo() {
        return saiuEm == null;
    }
}
