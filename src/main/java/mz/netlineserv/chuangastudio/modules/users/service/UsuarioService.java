package mz.netlineserv.chuangastudio.modules.users.service;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.exceptions.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.modules.users.domain.Usuario;
import mz.netlineserv.chuangastudio.modules.users.domain.PapelUsuario;
import mz.netlineserv.chuangastudio.modules.users.vm.UsuarioResponse;
import mz.netlineserv.chuangastudio.modules.users.repository.UsuarioRepository;
import mz.netlineserv.chuangastudio.shared.security.UsuarioAutenticado;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    /**
     * Retorna o Usuario correspondente ao utilizador autenticado na requisição atual.
     * Segue o mesmo padrão do userService.getOnlineUser() usado no SPO-BACK.
     */
    public Usuario obterUsuarioOnline() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof UsuarioAutenticado usuarioAutenticado)) {
            throw new RecursoNaoEncontradoException("Nenhum usuário autenticado na sessão atual.");
        }

        return buscarPorId(usuarioAutenticado.getId());
    }

    public List<UsuarioResponse> listarClientes() {
        return usuarioRepository.findByPapelAndDeletadoFalseOrderByCriadoEmDesc(PapelUsuario.CLIENTE)
                .stream()
                .map(UsuarioResponse::deEntidade)
                .collect(Collectors.toList());
    }

    public Usuario buscarPorId(UUID id) {
        return usuarioRepository.findById(id)
                .filter(usuario -> !usuario.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }
}
