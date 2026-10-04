package mz.netlineserv.chuangastudio.controller;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import mz.netlineserv.chuangastudio.common.PageResponse;
import mz.netlineserv.chuangastudio.domain.entity.Distrito;
import mz.netlineserv.chuangastudio.domain.entity.Provincia;
import mz.netlineserv.chuangastudio.repository.DistritoRepository;
import mz.netlineserv.chuangastudio.repository.ProvinciaRepository;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Classificadores de províncias e distritos (paginados e filtráveis, como no SPO). */
@RestController
@RequiredArgsConstructor
public class LocalizacaoController {

    public record ProvinciaResponse(Long id, String codigo, String nome, String sigla) {
        static ProvinciaResponse de(Provincia p) {
            return new ProvinciaResponse(p.getId(), p.getCodigo(), p.getNome(), p.getSigla());
        }
    }

    public record DistritoResponse(Long id, String codigo, String nome, Long provinciaId) {
        static DistritoResponse de(Distrito d) {
            return new DistritoResponse(d.getId(), d.getCodigo(), d.getNome(), d.getProvincia().getId());
        }
    }

    private final ProvinciaRepository provinciaRepository;
    private final DistritoRepository distritoRepository;

    @GetMapping("/publico/provincias")
    public ResponseEntity<ApiResponse<PageResponse<ProvinciaResponse>>> provincias(
            @RequestParam(required = false) String busca,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Specification<Provincia> spec = (root, query, cb) -> busca == null || busca.isBlank() ? null
                : cb.like(cb.lower(root.get("nome")), "%" + busca.trim().toLowerCase() + "%");
        return ResponseEntity.ok(ApiResponse.sucesso(PageResponse.from(
                provinciaRepository.findAll(spec, pageable).map(ProvinciaResponse::de))));
    }

    @GetMapping("/publico/distritos")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<DistritoResponse>>> distritos(
            @RequestParam(required = false) Long provinciaId,
            @RequestParam(required = false) String busca,
            @ParameterObject @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        Specification<Distrito> porProvincia = (root, query, cb) -> provinciaId == null ? null
                : cb.equal(root.get("provincia").get("id"), provinciaId);
        Specification<Distrito> porNome = (root, query, cb) -> busca == null || busca.isBlank() ? null
                : cb.like(cb.lower(root.get("nome")), "%" + busca.trim().toLowerCase() + "%");
        return ResponseEntity.ok(ApiResponse.sucesso(PageResponse.from(
                distritoRepository.findAll(Specification.allOf(porProvincia, porNome), pageable).map(DistritoResponse::de))));
    }
}
