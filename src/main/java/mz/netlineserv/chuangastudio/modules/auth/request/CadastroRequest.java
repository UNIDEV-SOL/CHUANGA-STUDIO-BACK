package mz.netlineserv.chuangastudio.modules.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mz.netlineserv.chuangastudio.shared.validators.Formatos;
import mz.netlineserv.chuangastudio.shared.validators.SenhaValida;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CadastroRequest {

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 150, message = "O nome pode ter até 150 caracteres")
    private String nome;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    @Size(max = 180, message = "O email pode ter até 180 caracteres")
    private String email;

    @NotBlank(message = "Telefone é obrigatório")
    @Pattern(regexp = Formatos.TELEFONE, message = Formatos.TELEFONE_MENSAGEM)
    private String telefone;

    @NotBlank(message = "Palavra-passe é obrigatória")
    @SenhaValida
    private String senha;

    /** Morada (opcional). */
    private Long provinciaId;

    private Long distritoId;
}
