package mz.netlineserv.chuangastudio.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mz.netlineserv.chuangastudio.common.validacao.Formatos;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CriarCotacaoRequest {

    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 150, message = "O nome pode ter até 150 caracteres")
    private String nome;

    @NotBlank(message = "Telefone é obrigatório")
    @Pattern(regexp = Formatos.TELEFONE, message = Formatos.TELEFONE_MENSAGEM)
    private String telefone;

    @Email(message = "Email inválido")
    @Size(max = 180, message = "O email pode ter até 180 caracteres")
    private String email;

    @Size(max = 2000, message = "A mensagem pode ter até 2000 caracteres")
    private String mensagem;

    @NotEmpty(message = "Selecione pelo menos um serviço")
    @Size(max = 20, message = "Pode pedir cotação de até 20 serviços de cada vez")
    private List<UUID> servicosIds;
}
