package mz.netlineserv.chuangastudio.modules.auth.vm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import mz.netlineserv.chuangastudio.modules.users.vm.UsuarioResponse;

@Getter
@AllArgsConstructor
public class AutenticacaoResponse {
    private String token;
    private UsuarioResponse usuario;
}
