package mz.netlineserv.chuangastudio.service.impl;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.exception.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;
import mz.netlineserv.chuangastudio.dto.response.UsuarioResponse;
import mz.netlineserv.chuangastudio.repository.UsuarioRepository;
import mz.netlineserv.chuangastudio.security.UsuarioAutenticado;
import mz.netlineserv.chuangastudio.service.UsuarioService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;

    /**
     * Retorna o Usuario correspondente ao utilizador autenticado na requisição atual.
     * Segue o mesmo padrão do userService.getOnlineUser() usado no SPO-BACK.
     */
    @Override
    public Usuario obterUsuarioOnline() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();

        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof UsuarioAutenticado usuarioAutenticado)) {
            throw new RecursoNaoEncontradoException("Nenhum usuário autenticado na sessão atual.");
        }

        return buscarPorId(usuarioAutenticado.getId());
    }

    @Override
    public List<UsuarioResponse> listarClientes() {
        return usuarioRepository.findByPapelAndDeletadoFalseOrderByCriadoEmDesc(PapelUsuario.CLIENTE)
                .stream()
                .map(UsuarioResponse::deEntidade)
                .collect(Collectors.toList());
    }

    @Override
    public Usuario buscarPorId(UUID id) {
        return usuarioRepository.findById(id)
                .filter(usuario -> !usuario.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }
}
