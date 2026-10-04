package mz.netlineserv.chuangastudio.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import mz.netlineserv.chuangastudio.dto.request.CadastroRequest;
import mz.netlineserv.chuangastudio.dto.request.LoginRequest;
import mz.netlineserv.chuangastudio.dto.request.RecuperarSenhaRequest;
import mz.netlineserv.chuangastudio.dto.request.RedefinirSenhaRequest;
import mz.netlineserv.chuangastudio.dto.response.AutenticacaoResponse;
import mz.netlineserv.chuangastudio.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/entrar")
    public ResponseEntity<ApiResponse<AutenticacaoResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso(authService.login(request)));
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<ApiResponse<AutenticacaoResponse>> cadastrar(@Valid @RequestBody CadastroRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso("Conta criada com sucesso.", authService.cadastrar(request)));
    }

    @PostMapping("/recuperar-senha")
    public ResponseEntity<ApiResponse<Void>> recuperarSenha(@Valid @RequestBody RecuperarSenhaRequest request) {
        authService.solicitarRecuperacaoSenha(request.email());
        return ResponseEntity.ok(ApiResponse.sucesso(
                "Se existir uma conta com este email, enviámos um link para escolher uma nova palavra-passe.", null));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<ApiResponse<Void>> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request) {
        authService.redefinirSenha(request.token(), request.novaSenha());
        return ResponseEntity.ok(ApiResponse.sucesso("Palavra-passe alterada. Já pode entrar.", null));
    }
}
