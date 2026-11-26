package mz.netlineserv.chuangastudio.modules.reports.controller;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.common.ApiResponse;
import mz.netlineserv.chuangastudio.modules.clients.vm.ClienteResumoResponse;
import mz.netlineserv.chuangastudio.modules.reports.vm.EstatisticasAdminResponse;
import mz.netlineserv.chuangastudio.modules.bookings.vm.MarcacaoResponse;
import mz.netlineserv.chuangastudio.modules.reports.service.AdminDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

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
    public ResponseEntity<ApiResponse<PageResponse<MarcacaoResponse>>> proximasMarcacoes(
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.sucesso(adminDashboardService.proximasMarcacoes(pageable)));
    }

    @GetMapping("/atividade-recente")
    public ResponseEntity<ApiResponse<PageResponse<MarcacaoResponse>>> atividadeRecente(
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.sucesso(adminDashboardService.atividadeRecente(pageable)));
    }

    @GetMapping("/clientes")
    public ResponseEntity<ApiResponse<PageResponse<ClienteResumoResponse>>> clientes(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long provinciaId,
            @RequestParam(required = false) Long distritoId,
            @ParameterObject @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.sucesso(adminDashboardService.listarClientes(busca, provinciaId, distritoId, pageable)));
    }
}
