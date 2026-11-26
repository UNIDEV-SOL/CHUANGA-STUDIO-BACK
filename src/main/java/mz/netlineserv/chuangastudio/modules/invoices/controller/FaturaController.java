package mz.netlineserv.chuangastudio.modules.invoices.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.common.ApiResponse;
import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import mz.netlineserv.chuangastudio.modules.invoices.domain.StatusFatura;
import mz.netlineserv.chuangastudio.modules.invoices.request.EmitirFaturaRequest;
import mz.netlineserv.chuangastudio.modules.invoices.request.ReenviarFaturaRequest;
import mz.netlineserv.chuangastudio.modules.invoices.request.RegistarPagamentoFaturaRequest;
import mz.netlineserv.chuangastudio.modules.invoices.vm.FaturaResponse;
import mz.netlineserv.chuangastudio.modules.invoices.service.FaturaService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FaturaController {

    private final FaturaService faturaService;

    // Fecho de conta na caixa: fatura com os serviços realmente feitos.
    @PostMapping("/admin/marcacoes/{marcacaoId}/fatura")
    public ResponseEntity<ApiResponse<FaturaResponse>> emitir(@PathVariable UUID marcacaoId,
                                                              @Valid @RequestBody EmitirFaturaRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso("Fatura emitida e enviada ao cliente.",
                faturaService.emitir(marcacaoId, request)));
    }

    @GetMapping("/admin/faturas")
    public ResponseEntity<ApiResponse<PageResponse<FaturaResponse>>> listarAdmin(
            @RequestParam(required = false) StatusFatura status,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dataInicio,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dataFim,
            @ParameterObject @PageableDefault(size = 20, sort = "dataEmissao", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.sucesso(faturaService.listarAdmin(status, busca, clienteId, dataInicio, dataFim, pageable)));
    }

    @GetMapping("/admin/faturas/{id}")
    public ResponseEntity<ApiResponse<FaturaResponse>> obterAdmin(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso(faturaService.obterAdmin(id)));
    }

    @PatchMapping("/admin/faturas/{id}/pagar")
    public ResponseEntity<ApiResponse<FaturaResponse>> registarPagamento(@PathVariable UUID id,
                                                                         @Valid @RequestBody RegistarPagamentoFaturaRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso("Pagamento registado. O recibo foi enviado ao cliente.",
                faturaService.registarPagamento(id, request)));
    }

    @PatchMapping("/admin/faturas/{id}/anular")
    public ResponseEntity<ApiResponse<FaturaResponse>> anular(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso("Fatura anulada.", faturaService.anular(id)));
    }

    @PostMapping("/admin/faturas/{id}/reenviar")
    public ResponseEntity<ApiResponse<Void>> reenviar(@PathVariable UUID id,
                                                      @Valid @RequestBody(required = false) ReenviarFaturaRequest request) {
        faturaService.reenviar(id, request != null ? request.getEmail() : null);
        return ResponseEntity.ok(ApiResponse.sucesso("Fatura reenviada.", null));
    }

    @GetMapping("/admin/faturas/{id}/pdf")
    public ResponseEntity<byte[]> pdfAdmin(@PathVariable UUID id) {
        return pdf(faturaService.pdfAdmin(id));
    }

    @GetMapping("/minha-area/faturas")
    public ResponseEntity<ApiResponse<PageResponse<FaturaResponse>>> minhasFaturas(
            @ParameterObject @PageableDefault(size = 20, sort = "dataEmissao", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.sucesso(faturaService.listarDoClienteOnline(pageable)));
    }

    @GetMapping("/minha-area/faturas/{id}/pdf")
    public ResponseEntity<byte[]> pdfCliente(@PathVariable UUID id) {
        return pdf(faturaService.pdfDoClienteOnline(id));
    }

    // Link enviado por email/WhatsApp: o token aleatório faz de chave de acesso.
    @GetMapping("/publico/faturas/{token}/pdf")
    public ResponseEntity<byte[]> pdfPublico(@PathVariable String token) {
        return pdf(faturaService.pdfPublico(token));
    }

    private static ResponseEntity<byte[]> pdf(FaturaService.Pdf pdf) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(pdf.nomeFicheiro()).build().toString())
                .body(pdf.conteudo());
    }
}
