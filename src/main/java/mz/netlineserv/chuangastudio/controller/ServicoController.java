package mz.netlineserv.chuangastudio.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import mz.netlineserv.chuangastudio.dto.request.ServicoRequest;
import mz.netlineserv.chuangastudio.dto.response.ServicoResponse;
import mz.netlineserv.chuangastudio.service.ServicoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ServicoController {

    private final ServicoService servicoService;

    // Endpoint público, consumido pelas páginas de Serviços, Cotação e Reserva do site.
    @GetMapping("/publico/servicos")
    public ResponseEntity<ApiResponse<List<ServicoResponse>>> listarPublico() {
        return ResponseEntity.ok(ApiResponse.sucesso(servicoService.listarPublico()));
    }

    @GetMapping("/admin/servicos")
    public ResponseEntity<ApiResponse<List<ServicoResponse>>> listarAdmin() {
        return ResponseEntity.ok(ApiResponse.sucesso(servicoService.listarTodosAdmin()));
    }

    @PostMapping("/admin/servicos")
    public ResponseEntity<ApiResponse<ServicoResponse>> criar(@Valid @RequestBody ServicoRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso("Serviço criado com sucesso.", servicoService.criar(request)));
    }

    @PutMapping("/admin/servicos/{id}")
    public ResponseEntity<ApiResponse<ServicoResponse>> atualizar(@PathVariable UUID id,
                                                                   @Valid @RequestBody ServicoRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso("Serviço atualizado com sucesso.", servicoService.atualizar(id, request)));
    }

    @DeleteMapping("/admin/servicos/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable UUID id) {
        servicoService.eliminar(id);
        return ResponseEntity.ok(ApiResponse.sucesso("Serviço eliminado com sucesso.", null));
    }
}
