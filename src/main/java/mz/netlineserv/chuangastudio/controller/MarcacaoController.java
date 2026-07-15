package mz.netlineserv.chuangastudio.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao;
import mz.netlineserv.chuangastudio.dto.request.AtualizarStatusMarcacaoRequest;
import mz.netlineserv.chuangastudio.dto.request.CriarMarcacaoRequest;
import mz.netlineserv.chuangastudio.dto.response.MarcacaoResponse;
import mz.netlineserv.chuangastudio.security.UsuarioAutenticado;
import mz.netlineserv.chuangastudio.service.MarcacaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MarcacaoController {

    private final MarcacaoService marcacaoService;

    // Passo final do wizard de /reservar. Funciona logado ou não (a lógica de
    // criar conta on-the-fly acontece no service quando não autenticado).
    @PostMapping("/publico/marcacoes")
    public ResponseEntity<ApiResponse<MarcacaoResponse>> criar(@Valid @RequestBody CriarMarcacaoRequest request) {
        boolean autenticado = usuarioAutenticadoNaSessao();
        return ResponseEntity.ok(ApiResponse.sucesso("Reserva registada com sucesso.",
                marcacaoService.criar(request, autenticado)));
    }

    @GetMapping("/minha-area/marcacoes")
    public ResponseEntity<ApiResponse<List<MarcacaoResponse>>> minhasMarcacoes() {
        return ResponseEntity.ok(ApiResponse.sucesso(marcacaoService.listarDoClienteOnline()));
    }

    @PatchMapping("/minha-area/marcacoes/{id}/cancelar")
    public ResponseEntity<ApiResponse<MarcacaoResponse>> cancelarMinhaMarcacao(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso("Marcação cancelada.", marcacaoService.cancelar(id)));
    }

    @GetMapping("/admin/marcacoes")
    public ResponseEntity<ApiResponse<List<MarcacaoResponse>>> listarAdmin(
            @RequestParam(required = false) StatusMarcacao status,
            @RequestParam(required = false) String busca) {
        return ResponseEntity.ok(ApiResponse.sucesso(marcacaoService.listarTodasAdmin(status, busca)));
    }

    @PatchMapping("/admin/marcacoes/{id}/status")
    public ResponseEntity<ApiResponse<MarcacaoResponse>> atualizarStatus(
            @PathVariable UUID id, @Valid @RequestBody AtualizarStatusMarcacaoRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso(marcacaoService.atualizarStatus(id, request.getStatus())));
    }

    @PatchMapping("/admin/marcacoes/{id}/confirmar-pagamento")
    public ResponseEntity<ApiResponse<MarcacaoResponse>> confirmarPagamento(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso("Pagamento confirmado.", marcacaoService.confirmarPagamento(id)));
    }

    @DeleteMapping("/admin/marcacoes/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable UUID id) {
        marcacaoService.eliminar(id);
        return ResponseEntity.ok(ApiResponse.sucesso("Marcação eliminada.", null));
    }

    private boolean usuarioAutenticadoNaSessao() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado;
    }
}
