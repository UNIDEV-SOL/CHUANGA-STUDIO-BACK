package mz.netlineserv.chuangastudio.controller;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import mz.netlineserv.chuangastudio.dto.response.RelatorioCaixaResponse;
import mz.netlineserv.chuangastudio.notificacao.RelatorioPdfRenderer;
import mz.netlineserv.chuangastudio.service.RelatorioService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService relatorioService;
    private final RelatorioPdfRenderer relatorioPdfRenderer;

    @GetMapping("/admin/relatorios/caixa")
    public ResponseEntity<ApiResponse<RelatorioCaixaResponse>> caixa(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(ApiResponse.sucesso(relatorioService.caixa(inicio, fim)));
    }

    @GetMapping("/admin/relatorios/caixa/pdf")
    public ResponseEntity<byte[]> caixaPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        RelatorioCaixaResponse relatorio = relatorioService.caixa(inicio, fim);
        byte[] pdf = relatorioPdfRenderer.gerar(relatorio, inicio.equals(fim) ? "Fecho do dia" : "Relatório do período");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(RelatorioPdfRenderer.nomeFicheiro(relatorio)).build().toString())
                .body(pdf);
    }
}
