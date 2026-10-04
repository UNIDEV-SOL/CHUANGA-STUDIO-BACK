package mz.netlineserv.chuangastudio.avaliacao;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import mz.netlineserv.chuangastudio.common.PageResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    @GetMapping("/publico/avaliacoes")
    public ResponseEntity<ApiResponse<PageResponse<AvaliacaoService.AvaliacaoResponse>>> publicas(
            @ParameterObject @PageableDefault(size = 6, sort = "respondidaEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.sucesso(avaliacaoService.publicas(pageable)));
    }

    @GetMapping("/publico/avaliacoes/resumo")
    public ResponseEntity<ApiResponse<AvaliacaoService.Resumo>> resumo() {
        return ResponseEntity.ok(ApiResponse.sucesso(avaliacaoService.resumo()));
    }

    /** Página do link do email: o token identifica a marcação (sem login). */
    @GetMapping("/publico/avaliacoes/pedido/{token}")
    public ResponseEntity<ApiResponse<AvaliacaoService.PedidoPublico>> pedido(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.sucesso(avaliacaoService.pedido(token)));
    }

    @PostMapping("/publico/avaliacoes/pedido/{token}")
    public ResponseEntity<ApiResponse<Void>> responder(@PathVariable String token, @RequestBody AvaliacaoService.ResponderRequest request) {
        avaliacaoService.responder(token, request);
        return ResponseEntity.ok(ApiResponse.sucesso("Obrigada pela sua avaliação!", null));
    }

    @GetMapping("/admin/avaliacoes")
    public ResponseEntity<ApiResponse<PageResponse<AvaliacaoService.AvaliacaoResponse>>> admin(
            @RequestParam(required = false) Short nota,
            @RequestParam(required = false) Boolean publicada,
            @RequestParam(required = false) Boolean respondida,
            @ParameterObject @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.sucesso(avaliacaoService.admin(nota, publicada, respondida, pageable)));
    }

    @PatchMapping("/admin/avaliacoes/{id}/publicacao")
    public ResponseEntity<ApiResponse<AvaliacaoService.AvaliacaoResponse>> publicar(@PathVariable UUID id, @RequestBody Map<String, Boolean> corpo) {
        boolean publicar = Boolean.TRUE.equals(corpo.get("publicada"));
        return ResponseEntity.ok(ApiResponse.sucesso(publicar ? "Avaliação publicada no site." : "Avaliação retirada do site.",
                avaliacaoService.publicar(id, publicar)));
    }
}
