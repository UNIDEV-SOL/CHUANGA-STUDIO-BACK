package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.dto.request.CadastroRequest;
import mz.netlineserv.chuangastudio.dto.request.LoginRequest;
import mz.netlineserv.chuangastudio.dto.response.AutenticacaoResponse;

public interface AuthService {
    AutenticacaoResponse login(LoginRequest request);
    AutenticacaoResponse cadastrar(CadastroRequest request);

    /** Envia o link por email se a conta existir; responde sempre igual para não revelar que emails estão registados. */
    void solicitarRecuperacaoSenha(String email);

    void redefinirSenha(String token, String novaSenha);
}
