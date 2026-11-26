package mz.netlineserv.chuangastudio.modules.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mz.netlineserv.chuangastudio.shared.validators.SenhaValida;

/** Último passo da recuperação: o token vem da confirmação do código OTP. */
public record RedefinirSenhaRequest(
        @NotBlank(message = "Falta o token da confirmação do código. Comece de novo e peça um código.")
        @Size(max = 100, message = "Token de recuperação inválido") String token,
        @NotBlank(message = "Palavra-passe é obrigatória") @SenhaValida String novaSenha,
        @NotBlank(message = "Confirme a nova palavra-passe") String confirmacaoSenha) {
}
