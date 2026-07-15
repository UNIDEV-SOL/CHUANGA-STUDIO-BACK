package mz.netlineserv.chuangastudio.controller;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import mz.netlineserv.chuangastudio.dto.response.ClienteResumoResponse;
import mz.netlineserv.chuangastudio.dto.response.EstatisticasAdminResponse;
import mz.netlineserv.chuangastudio.dto.response.MarcacaoResponse;
import mz.netlineserv.chuangastudio.service.AdminDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/estatisticas")
    public ResponseEntity<ApiResponse<EstatisticasAdminResponse>> estatisticas() {
        return ResponseEntity.ok(ApiResponse.sucesso(adminDashboardService.obterEstatisticas()));
    }

    @GetMapping("/marcacoes/proximas")
    public ResponseEntity<ApiResponse<List<MarcacaoResponse>>> proximasMarcacoes() {
        return ResponseEntity.ok(ApiResponse.sucesso(adminDashboardService.proximasMarcacoes()));
    }

    @GetMapping("/atividade-recente")
    public ResponseEntity<ApiResponse<List<MarcacaoResponse>>> atividadeRecente() {
        return ResponseEntity.ok(ApiResponse.sucesso(adminDashboardService.atividadeRecente()));
    }

    @GetMapping("/clientes")
    public ResponseEntity<ApiResponse<List<ClienteResumoResponse>>> clientes() {
        return ResponseEntity.ok(ApiResponse.sucesso(adminDashboardService.listarClientesComResumo()));
    }
}
