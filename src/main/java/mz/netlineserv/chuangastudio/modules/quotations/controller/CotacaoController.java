package mz.netlineserv.chuangastudio.modules.quotations.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.common.ApiResponse;
import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import mz.netlineserv.chuangastudio.modules.quotations.domain.StatusCotacao;
import mz.netlineserv.chuangastudio.modules.quotations.request.CriarCotacaoRequest;
import mz.netlineserv.chuangastudio.modules.quotations.vm.CotacaoResponse;
import mz.netlineserv.chuangastudio.modules.quotations.service.CotacaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CotacaoController {

    private final CotacaoService cotacaoService;

    // Usado pela página pública /cotacao
    @PostMapping("/publico/cotacoes")
    public ResponseEntity<ApiResponse<CotacaoResponse>> criar(@Valid @RequestBody CriarCotacaoRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso("Cotação enviada com sucesso.", cotacaoService.criar(request)));
    }

    @GetMapping("/admin/cotacoes")
    public ResponseEntity<ApiResponse<PageResponse<CotacaoResponse>>> listarAdmin(
            @RequestParam(required = false) StatusCotacao status,
            @RequestParam(required = false) String busca,
            @ParameterObject @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.sucesso(cotacaoService.listarAdmin(status, busca, pageable)));
    }

    @PatchMapping("/admin/cotacoes/{id}/respondido")
    public ResponseEntity<ApiResponse<CotacaoResponse>> marcarComoRespondido(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso(cotacaoService.marcarComoRespondido(id)));
    }

    @PatchMapping("/admin/cotacoes/{id}/arquivar")
    public ResponseEntity<ApiResponse<CotacaoResponse>> arquivar(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso(cotacaoService.arquivar(id)));
    }
}
