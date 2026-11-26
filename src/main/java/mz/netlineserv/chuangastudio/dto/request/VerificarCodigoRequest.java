package mz.netlineserv.chuangastudio.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerificarCodigoRequest(
        @NotBlank(message = "Email é obrigatório") @Email(message = "Email inválido")
        @Size(max = 180, message = "O email pode ter até 180 caracteres") String email,
        @NotBlank(message = "Indique o código que recebeu por email")
        @Pattern(regexp = "^\\s*\\d{6}\\s*$", message = "O código tem 6 dígitos (só números)") String codigo) {
}
