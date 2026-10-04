package mz.netlineserv.chuangastudio.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CadastroRequest {

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    private String email;

    @NotBlank(message = "Telefone é obrigatório")
    private String telefone;

    @NotBlank(message = "Palavra-passe é obrigatória")
    @Size(min = 6, message = "Palavra-passe deve ter pelo menos 6 caracteres")
    private String senha;

    /** Morada (opcional). */
    private Long provinciaId;

    private Long distritoId;
}
