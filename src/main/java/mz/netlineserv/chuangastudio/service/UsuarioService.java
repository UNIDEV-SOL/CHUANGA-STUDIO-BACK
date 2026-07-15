package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.dto.response.UsuarioResponse;

import java.util.List;
import java.util.UUID;

public interface UsuarioService {

    Usuario obterUsuarioOnline();

    List<UsuarioResponse> listarClientes();

    Usuario buscarPorId(UUID id);
}
