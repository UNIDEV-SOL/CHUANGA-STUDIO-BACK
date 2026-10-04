package mz.netlineserv.chuangastudio.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "chat_anexos")
public class ChatAnexo {

    @Id
    private UUID id;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    /** Nome original (documentos). */
    @Column(length = 200)
    private String nome;

    private Long tamanho;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "BYTEA")
    private byte[] dados;
}
