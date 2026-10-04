package mz.netlineserv.chuangastudio.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedefinirSenhaRequest(
        @NotBlank(message = "Link inválido") String token,
        @NotBlank(message = "Palavra-passe é obrigatória")
        @Size(min = 6, max = 64, message = "A palavra-passe deve ter entre 6 e 64 caracteres") String novaSenha) {
}
