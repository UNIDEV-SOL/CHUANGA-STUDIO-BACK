package mz.netlineserv.chuangastudio.modules.gallery.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Bytes de uma foto da galeria (versão grande e miniatura). Só é lida quando se serve a imagem. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "galeria_imagens")
public class GaleriaImagem {

    @Id
    private UUID id;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(nullable = false, columnDefinition = "BYTEA")
    private byte[] dados;

    @Column(nullable = false, columnDefinition = "BYTEA")
    private byte[] miniatura;
}
