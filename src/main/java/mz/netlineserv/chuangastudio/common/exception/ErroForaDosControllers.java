package mz.netlineserv.chuangastudio.common.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Substitui o /error do Spring Boot: erros que acontecem fora dos controllers (filtros, servlet)
 * chegam aqui e saem no mesmo formato da API, com o motivo, em vez da página/JSON genérico.
 */
@Slf4j
@RestController
public class ErroForaDosControllers implements ErrorController {

    @Value("${app.erros.detalhados:false}")
    private boolean detalhados;

    @RequestMapping("/error")
    public ResponseEntity<ApiResponse<Object>> erro(HttpServletRequest req) {
        Object codigoHttp = req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus estado = codigoHttp instanceof Integer c && HttpStatus.resolve(c) != null ? HttpStatus.valueOf(c) : HttpStatus.INTERNAL_SERVER_ERROR;
        String uri = String.valueOf(req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        Throwable ex = (Throwable) req.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        if (estado.is5xxServerError()) {
            String ref = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.error("[{}] Erro fora dos controllers em {} ({})", ref, uri, estado.value(), ex);
            String tipo = ex != null ? ex.getClass().getSimpleName() : "sem exceção";
            String mensagem = detalhados && ex != null
                    ? "Erro inesperado (ref. " + ref + ") em " + uri + ": " + tipo + " — " + ex.getMessage()
                    : "Erro inesperado no servidor (ref. " + ref + ", " + tipo + ") em " + uri + ". O detalhe ficou no log com esta referência.";
            return ResponseEntity.status(estado).body(ApiResponse.erro("ERRO_INTERNO", mensagem, Map.of("referencia", ref, "tipo", tipo)));
        }
        String mensagem = estado == HttpStatus.NOT_FOUND
                ? "Não existe nenhum endpoint em " + uri + "."
                : "O pedido a " + uri + " falhou com o estado " + estado.value() + " (" + estado.getReasonPhrase() + ")"
                + (req.getAttribute(RequestDispatcher.ERROR_MESSAGE) instanceof String m && !m.isBlank() ? ": " + m : "") + ".";
        return ResponseEntity.status(estado).body(ApiResponse.erro(estado == HttpStatus.NOT_FOUND ? "ENDPOINT_INEXISTENTE" : "ERRO_" + estado.value(), mensagem));
    }
}
