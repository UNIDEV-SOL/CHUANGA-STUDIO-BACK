package mz.netlineserv.chuangastudio.modules.bookings.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.common.ApiResponse;
import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import mz.netlineserv.chuangastudio.modules.bookings.request.FiltroMarcacoes;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao;
import mz.netlineserv.chuangastudio.modules.bookings.request.AtualizarStatusMarcacaoRequest;
import mz.netlineserv.chuangastudio.modules.bookings.request.CriarMarcacaoRequest;
import mz.netlineserv.chuangastudio.modules.bookings.request.RemarcarRequest;
import mz.netlineserv.chuangastudio.modules.bookings.vm.MarcacaoResponse;
import mz.netlineserv.chuangastudio.shared.security.UsuarioAutenticado;
import mz.netlineserv.chuangastudio.modules.bookings.service.MarcacaoService;
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
    public ResponseEntity<ApiResponse<PageResponse<MarcacaoResponse>>> minhasMarcacoes(
            @ParameterObject @PageableDefault(size = 20, sort = {"dataMarcacao", "horaMarcacao"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.sucesso(marcacaoService.listarDoClienteOnline(pageable)));
    }

    @PatchMapping("/minha-area/marcacoes/{id}/cancelar")
    public ResponseEntity<ApiResponse<MarcacaoResponse>> cancelarMinhaMarcacao(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso("Marcação cancelada.", marcacaoService.cancelarDoClienteOnline(id)));
    }

    @PatchMapping("/minha-area/marcacoes/{id}/remarcar")
    public ResponseEntity<ApiResponse<MarcacaoResponse>> remarcarMinhaMarcacao(@PathVariable UUID id,
                                                                              @Valid @RequestBody RemarcarRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso("Marcação remarcada.",
                marcacaoService.remarcarDoClienteOnline(id, request.data(), request.hora())));
    }

    @PatchMapping("/admin/marcacoes/{id}/remarcar")
    public ResponseEntity<ApiResponse<MarcacaoResponse>> remarcarAdmin(@PathVariable UUID id,
                                                                      @Valid @RequestBody RemarcarRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso("Marcação remarcada. A cliente foi avisada por email.",
                marcacaoService.remarcarAdmin(id, request.data(), request.hora())));
    }

    @PatchMapping("/admin/marcacoes/{id}/profissional")
    public ResponseEntity<ApiResponse<MarcacaoResponse>> atribuirProfissional(@PathVariable UUID id,
                                                                             @RequestBody java.util.Map<String, UUID> corpo) {
        return ResponseEntity.ok(ApiResponse.sucesso("Profissional atualizada.",
                marcacaoService.atribuirProfissional(id, corpo.get("profissionalId"))));
    }

    @GetMapping("/admin/marcacoes")
    public ResponseEntity<ApiResponse<PageResponse<MarcacaoResponse>>> listarAdmin(
            @RequestParam(required = false) StatusMarcacao status,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) UUID profissionalId,
            @RequestParam(required = false) Boolean semProfissional,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @ParameterObject @PageableDefault(size = 20, sort = {"dataMarcacao", "horaMarcacao"}, direction = Sort.Direction.DESC) Pageable pageable) {
        FiltroMarcacoes filtro = new FiltroMarcacoes(status, busca, profissionalId, semProfissional, clienteId, dataInicio, dataFim);
        return ResponseEntity.ok(ApiResponse.sucesso(marcacaoService.listarAdmin(filtro, pageable)));
    }

    @GetMapping("/admin/marcacoes/{id}")
    public ResponseEntity<ApiResponse<MarcacaoResponse>> obterAdmin(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso(marcacaoService.obterAdmin(id)));
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
