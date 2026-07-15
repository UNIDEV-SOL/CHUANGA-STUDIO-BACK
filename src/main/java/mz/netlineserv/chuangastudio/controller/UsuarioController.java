package mz.netlineserv.chuangastudio.controller;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import mz.netlineserv.chuangastudio.dto.response.UsuarioResponse;
import mz.netlineserv.chuangastudio.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    // Consumido no carregamento do AuthContext / rota /minha-area
    @GetMapping("/minha-area/perfil")
    public ResponseEntity<ApiResponse<UsuarioResponse>> perfil() {
        return ResponseEntity.ok(ApiResponse.sucesso(UsuarioResponse.deEntidade(usuarioService.obterUsuarioOnline())));
    }
}
