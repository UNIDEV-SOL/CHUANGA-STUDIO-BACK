package mz.netlineserv.chuangastudio.modules.schedule.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.common.ApiResponse;
import mz.netlineserv.chuangastudio.modules.schedule.request.AtualizarHorarioRequest;
import mz.netlineserv.chuangastudio.modules.schedule.request.DiaFechadoRequest;
import mz.netlineserv.chuangastudio.modules.schedule.vm.ConfiguracaoHorarioResponse;
import mz.netlineserv.chuangastudio.modules.schedule.vm.DisponibilidadeResponse;
import mz.netlineserv.chuangastudio.modules.schedule.service.AgendaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AgendaController {

    private final AgendaService agendaService;

    // Passo "Data & hora" da reserva: horas de início livres para os serviços escolhidos.
    @GetMapping("/publico/disponibilidade")
    public ResponseEntity<ApiResponse<DisponibilidadeResponse>> disponibilidade(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) List<UUID> servicosIds,
            @RequestParam(required = false) UUID ignorarMarcacaoId,
            @RequestParam(required = false) UUID profissionalId) {
        return ResponseEntity.ok(ApiResponse.sucesso(agendaService.disponibilidade(data, servicosIds, ignorarMarcacaoId, profissionalId)));
    }

    @GetMapping("/admin/horario")
    public ResponseEntity<ApiResponse<ConfiguracaoHorarioResponse>> configuracao() {
        return ResponseEntity.ok(ApiResponse.sucesso(agendaService.configuracao()));
    }

    @PutMapping("/admin/horario")
    public ResponseEntity<ApiResponse<ConfiguracaoHorarioResponse>> atualizarHorario(
            @Valid @RequestBody AtualizarHorarioRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso("Horário atualizado.", agendaService.atualizarHorario(request)));
    }

    @PostMapping("/admin/dias-fechados")
    public ResponseEntity<ApiResponse<ConfiguracaoHorarioResponse>> adicionarDiaFechado(
            @Valid @RequestBody DiaFechadoRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso("Dia fechado adicionado.", agendaService.adicionarDiaFechado(request)));
    }

    @DeleteMapping("/admin/dias-fechados/{id}")
    public ResponseEntity<ApiResponse<ConfiguracaoHorarioResponse>> removerDiaFechado(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso("Dia fechado removido.", agendaService.removerDiaFechado(id)));
    }
}
