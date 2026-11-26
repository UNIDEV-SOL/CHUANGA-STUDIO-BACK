package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.dto.request.CadastroRequest;
import mz.netlineserv.chuangastudio.dto.request.LoginRequest;
import mz.netlineserv.chuangastudio.dto.response.AutenticacaoResponse;

public interface AuthService {
    AutenticacaoResponse login(LoginRequest request);
    AutenticacaoResponse cadastrar(CadastroRequest request);

    /** Envia o código OTP por email se a conta existir; responde sempre igual para não revelar que emails estão registados. */
    void solicitarRecuperacaoSenha(String email);

    /** Confirma o código do email; devolve o token curto que autoriza escolher a nova palavra-passe. */
    String verificarCodigoRecuperacao(String email, String codigo);

    /** Grava a nova palavra-passe e inicia sessão. */
    AutenticacaoResponse redefinirSenha(String token, String novaSenha, String confirmacaoSenha);
}
