package mz.netlineserv.chuangastudio.modules.loyalty.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.common.ApiResponse;
import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import mz.netlineserv.chuangastudio.modules.users.service.UsuarioService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import mz.netlineserv.chuangastudio.modules.loyalty.domain.Vale;
import mz.netlineserv.chuangastudio.modules.loyalty.service.FidelizacaoService;
import mz.netlineserv.chuangastudio.modules.loyalty.service.ValeService;

@RestController
@RequiredArgsConstructor
public class FidelizacaoController {

    private final FidelizacaoService fidelizacaoService;
    private final ValeService valeService;
    private final UsuarioService usuarioService;

    // --- Fidelização ---

    @GetMapping("/admin/fidelizacao/config")
    public ResponseEntity<ApiResponse<FidelizacaoService.ConfigDto>> config() {
        return ResponseEntity.ok(ApiResponse.sucesso(fidelizacaoService.config()));
    }

    @PutMapping("/admin/fidelizacao/config")
    public ResponseEntity<ApiResponse<FidelizacaoService.ConfigDto>> atualizarConfig(@RequestBody FidelizacaoService.ConfigDto dto) {
        return ResponseEntity.ok(ApiResponse.sucesso("Programa de fidelização atualizado.", fidelizacaoService.atualizar(dto)));
    }

    @GetMapping("/admin/clientes/{id}/fidelizacao")
    public ResponseEntity<ApiResponse<FidelizacaoService.EstadoCliente>> doCliente(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso(fidelizacaoService.estado(id)));
    }

    @GetMapping("/minha-area/fidelizacao")
    public ResponseEntity<ApiResponse<FidelizacaoService.EstadoCliente>> minha() {
        return ResponseEntity.ok(ApiResponse.sucesso(fidelizacaoService.estado(usuarioService.obterUsuarioOnline().getId())));
    }

    // --- Vales-oferta ---

    @GetMapping("/admin/vales")
    public ResponseEntity<ApiResponse<PageResponse<ValeService.ValeResponse>>> vales(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Vale.Estado estado,
            @ParameterObject @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.sucesso(valeService.listar(busca, estado, pageable)));
    }

    @PostMapping("/admin/vales")
    public ResponseEntity<ApiResponse<ValeService.ValeResponse>> criar(@Valid @RequestBody ValeService.CriarValeRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso("Vale emitido.", valeService.criar(request)));
    }

    @PatchMapping("/admin/vales/{id}/anular")
    public ResponseEntity<ApiResponse<ValeService.ValeResponse>> anular(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso("Vale anulado.", valeService.anular(id)));
    }

    /** Fecho de conta: confirma o código e mostra o saldo. */
    @GetMapping("/admin/vales/validar")
    public ResponseEntity<ApiResponse<ValeService.SaldoPublico>> validar(@RequestParam String codigo) {
        valeService.validarParaUso(codigo);
        return ResponseEntity.ok(ApiResponse.sucesso(valeService.saldo(codigo)));
    }

    /** Qualquer pessoa com o código consulta o saldo no site. */
    @GetMapping("/publico/vales/{codigo}")
    public ResponseEntity<ApiResponse<ValeService.SaldoPublico>> saldo(@PathVariable String codigo) {
        return ResponseEntity.ok(ApiResponse.sucesso(valeService.saldo(codigo)));
    }
}
