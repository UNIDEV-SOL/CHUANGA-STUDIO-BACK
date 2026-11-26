package mz.netlineserv.chuangastudio.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import mz.netlineserv.chuangastudio.dto.request.CadastroRequest;
import mz.netlineserv.chuangastudio.dto.request.LoginRequest;
import mz.netlineserv.chuangastudio.dto.request.RecuperarSenhaRequest;
import mz.netlineserv.chuangastudio.dto.request.RedefinirSenhaRequest;
import mz.netlineserv.chuangastudio.dto.request.VerificarCodigoRequest;
import mz.netlineserv.chuangastudio.dto.response.AutenticacaoResponse;
import mz.netlineserv.chuangastudio.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.seguranca.recuperacao-senha-minutos:10}")
    private int validadeCodigoMinutos;

    @PostMapping("/entrar")
    public ResponseEntity<ApiResponse<AutenticacaoResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso(authService.login(request)));
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<ApiResponse<AutenticacaoResponse>> cadastrar(@Valid @RequestBody CadastroRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso("Conta criada com sucesso.", authService.cadastrar(request)));
    }

    /** Passo 1: envia o código OTP de 6 dígitos para o email (a resposta é igual exista ou não a conta). */
    @PostMapping("/recuperar-senha")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> recuperarSenha(@Valid @RequestBody RecuperarSenhaRequest request) {
        authService.solicitarRecuperacaoSenha(request.email());
        return ResponseEntity.ok(ApiResponse.sucesso(
                "Se existir uma conta com este email, enviámos um código de 6 dígitos válido durante "
                        + validadeCodigoMinutos + " minutos.",
                Map.of("validadeMinutos", validadeCodigoMinutos)));
    }

    /** Passo 2: confirma o código; devolve o token que autoriza o passo 3. */
    @PostMapping("/recuperar-senha/verificar")
    public ResponseEntity<ApiResponse<Map<String, String>>> verificarCodigo(@Valid @RequestBody VerificarCodigoRequest request) {
        String token = authService.verificarCodigoRecuperacao(request.email(), request.codigo());
        return ResponseEntity.ok(ApiResponse.sucesso("Código confirmado. Escolha a nova palavra-passe.", Map.of("token", token)));
    }

    /** Passo 3: nova palavra-passe + confirmação; já devolve a sessão iniciada. */
    @PostMapping("/redefinir-senha")
    public ResponseEntity<ApiResponse<AutenticacaoResponse>> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso("Palavra-passe alterada. Sessão iniciada.",
                authService.redefinirSenha(request.token(), request.novaSenha(), request.confirmacaoSenha())));
    }
}
