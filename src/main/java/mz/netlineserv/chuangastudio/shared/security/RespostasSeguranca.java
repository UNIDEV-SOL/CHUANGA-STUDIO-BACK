package mz.netlineserv.chuangastudio.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.shared.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Respostas 401/403 da cadeia de segurança no mesmo formato JSON do resto da API, com o motivo exato. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RespostasSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** 401: o pedido chegou a um endpoint protegido sem sessão válida. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        FalhaAutenticacao falha = FalhaAutenticacao.doPedido(request);
        if (falha == null) {
            falha = new FalhaAutenticacao("SEM_SESSAO",
                    "Precisa de iniciar sessão para aceder a " + request.getMethod() + " " + caminho(request) + " (falta o cabeçalho Authorization).");
        }
        escrever(response, HttpStatus.UNAUTHORIZED, ApiResponse.erro(falha.codigo(), falha.mensagem()));
    }

    /** 403: há sessão, mas o papel da conta não chega para este endpoint. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String papel = auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u ? u.getPapel() : "desconhecido";
        String caminho = caminho(request);
        String exigido = caminho.startsWith("/admin/") ? "ADMIN" : caminho.startsWith("/minha-area/") ? "CLIENTE" : null;
        String mensagem = exigido != null
                ? "Esta área é só para contas " + exigido + "; a sua sessão é de uma conta " + papel + "."
                : "A sua conta (" + papel + ") não tem permissão para " + request.getMethod() + " " + caminho + ".";
        log.info("Acesso negado: {} {} por papel {}", request.getMethod(), caminho, papel);
        escrever(response, HttpStatus.FORBIDDEN, ApiResponse.erro("SEM_PERMISSAO", mensagem));
    }

    public void escrever(HttpServletResponse response, HttpStatus status, ApiResponse<?> corpo) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), corpo);
    }

    private static String caminho(HttpServletRequest request) {
        return request.getServletPath() == null || request.getServletPath().isEmpty() ? request.getRequestURI() : request.getServletPath();
    }
}
