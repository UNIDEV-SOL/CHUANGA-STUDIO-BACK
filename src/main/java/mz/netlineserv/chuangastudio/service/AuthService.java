package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.dto.request.CadastroRequest;
import mz.netlineserv.chuangastudio.dto.request.LoginRequest;
import mz.netlineserv.chuangastudio.dto.response.AutenticacaoResponse;

public interface AuthService {
    AutenticacaoResponse login(LoginRequest request);
    AutenticacaoResponse cadastrar(CadastroRequest request);
}
