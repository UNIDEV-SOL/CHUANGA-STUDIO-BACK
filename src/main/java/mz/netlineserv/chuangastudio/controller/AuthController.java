package mz.netlineserv.chuangastudio.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import mz.netlineserv.chuangastudio.dto.request.CadastroRequest;
import mz.netlineserv.chuangastudio.dto.request.LoginRequest;
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
}
