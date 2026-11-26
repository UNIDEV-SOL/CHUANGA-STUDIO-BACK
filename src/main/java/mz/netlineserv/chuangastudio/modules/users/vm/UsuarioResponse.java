package mz.netlineserv.chuangastudio.modules.users.vm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import mz.netlineserv.chuangastudio.modules.users.domain.Usuario;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class UsuarioResponse {

    private UUID id;
    private String nome;
    private String email;
    private String telefone;
    private String papel;
    private boolean contaAtiva;
    private LocalDateTime criadoEm;

    public static UsuarioResponse deEntidade(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                usuario.getPapel().name(),
                usuario.isContaAtiva(),
                usuario.getCriadoEm()
        );
    }
}
