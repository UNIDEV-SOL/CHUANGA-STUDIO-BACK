package mz.netlineserv.chuangastudio.modules.professionals.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfissionalRequest(
        @NotBlank(message = "Nome é obrigatório") @Size(max = 120, message = "Nome demasiado longo") String nome,
        @Size(max = 30, message = "Telefone demasiado longo") String telefone,
        Boolean ativo,
        Integer ordem) {
}
