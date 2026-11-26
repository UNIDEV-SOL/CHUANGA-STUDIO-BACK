package mz.netlineserv.chuangastudio.modules.professionals.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.common.ApiResponse;
import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import mz.netlineserv.chuangastudio.shared.persistence.BaseSpecification;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import mz.netlineserv.chuangastudio.shared.exceptions.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.modules.professionals.domain.Profissional;
import mz.netlineserv.chuangastudio.modules.professionals.request.ProfissionalRequest;
import mz.netlineserv.chuangastudio.modules.professionals.vm.ProfissionalResponse;
import mz.netlineserv.chuangastudio.modules.professionals.repository.ProfissionalRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Equipa do estúdio. CRUD simples: sem regras além de validar o pedido, fica no controller. */
@RestController
@RequiredArgsConstructor
public class ProfissionalController {

    private final ProfissionalRepository profissionalRepository;

    @GetMapping("/publico/profissionais")
    public ResponseEntity<ApiResponse<PageResponse<ProfissionalResponse>>> ativas(
            @ParameterObject @PageableDefault(size = 20, sort = {"ordem", "nome"}) Pageable pageable) {
        var spec = BaseSpecification.of(Profissional.class).equals("ativo", true).build();
        return ResponseEntity.ok(ApiResponse.sucesso(PageResponse.from(
                profissionalRepository.findAll(spec, pageable).map(ProfissionalResponse::publica))));
    }

    @GetMapping("/admin/profissionais")
    public ResponseEntity<ApiResponse<PageResponse<ProfissionalResponse>>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @ParameterObject @PageableDefault(size = 20, sort = {"ordem", "nome"}) Pageable pageable) {
        var spec = BaseSpecification.of(Profissional.class).likeAny(busca, "nome", "telefone").equals("ativo", ativo).build();
        return ResponseEntity.ok(ApiResponse.sucesso(PageResponse.from(
                profissionalRepository.findAll(spec, pageable).map(ProfissionalResponse::deEntidade))));
    }

    @PostMapping("/admin/profissionais")
    @Transactional
    public ResponseEntity<ApiResponse<ProfissionalResponse>> criar(@Valid @RequestBody ProfissionalRequest request) {
        Profissional p = new Profissional();
        aplicar(p, request);
        return ResponseEntity.ok(ApiResponse.sucesso("Profissional adicionada.",
                ProfissionalResponse.deEntidade(profissionalRepository.save(p))));
    }

    @PutMapping("/admin/profissionais/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<ProfissionalResponse>> atualizar(@PathVariable UUID id,
                                                                      @Valid @RequestBody ProfissionalRequest request) {
        Profissional p = buscar(id);
        aplicar(p, request);
        return ResponseEntity.ok(ApiResponse.sucesso("Profissional atualizada.",
                ProfissionalResponse.deEntidade(profissionalRepository.save(p))));
    }

    // Soft delete: as marcações antigas continuam a mostrar quem as fez.
    @DeleteMapping("/admin/profissionais/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> remover(@PathVariable UUID id) {
        Profissional p = buscar(id);
        p.setAtivo(false);
        p.marcarComoDeletado();
        profissionalRepository.save(p);
        return ResponseEntity.ok(ApiResponse.sucesso("Profissional removida.", null));
    }

    private static void aplicar(Profissional p, ProfissionalRequest request) {
        p.setNome(request.nome().trim());
        p.setTelefone(request.telefone() == null || request.telefone().isBlank() ? null : request.telefone().trim());
        if (request.ativo() != null) {
            p.setAtivo(request.ativo());
        }
        if (request.ordem() != null) {
            p.setOrdem(request.ordem());
        }
    }

    private Profissional buscar(UUID id) {
        return profissionalRepository.findById(id)
                .filter(p -> !p.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional não encontrada."));
    }
}
