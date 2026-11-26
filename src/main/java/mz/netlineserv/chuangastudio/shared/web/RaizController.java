package mz.netlineserv.chuangastudio.shared.web;

import mz.netlineserv.chuangastudio.shared.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Quem abre a raiz da API vai para a documentação (quando está ativa) em vez de receber um 401. */
@RestController
public class RaizController {

    @Value("${springdoc.swagger-ui.enabled:true}")
    private boolean documentacaoAtiva;

    @GetMapping("/")
    public ResponseEntity<?> raiz() {
        if (documentacaoAtiva) {
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "docs").build();
        }
        return ResponseEntity.ok(ApiResponse.sucesso("API do Chuanga Studio. A documentação está desativada neste ambiente.",
                Map.of("estado", "ativa")));
    }
}
