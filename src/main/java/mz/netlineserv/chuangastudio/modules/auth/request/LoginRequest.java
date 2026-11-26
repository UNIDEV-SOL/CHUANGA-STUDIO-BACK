package mz.netlineserv.chuangastudio.modules.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    @Size(max = 180, message = "O email pode ter até 180 caracteres")
    private String email;

    @NotBlank(message = "Palavra-passe é obrigatória")
    @Size(max = 200, message = "Palavra-passe demasiado longa")
    private String senha;
}
