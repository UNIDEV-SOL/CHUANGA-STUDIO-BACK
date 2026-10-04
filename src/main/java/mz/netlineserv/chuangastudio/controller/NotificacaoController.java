package mz.netlineserv.chuangastudio.controller;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import mz.netlineserv.chuangastudio.common.PageResponse;
import mz.netlineserv.chuangastudio.domain.entity.Notificacao;
import mz.netlineserv.chuangastudio.domain.entity.NotificacaoLeitura;
import mz.netlineserv.chuangastudio.domain.enums.TipoNotificacao;
import mz.netlineserv.chuangastudio.repository.NotificacaoRepository;
import mz.netlineserv.chuangastudio.repository.spec.BaseSpecification;
import mz.netlineserv.chuangastudio.service.UsuarioService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Sino de notificações do painel. A leitura é por utilizador. */
@RestController
@RequiredArgsConstructor
public class NotificacaoController {

    public record NotificacaoResponse(UUID id, String tipo, String titulo, String mensagem, String link,
                                      LocalDateTime criadoEm, boolean lida) {
    }

    public record ResumoResponse(long naoLidas) {
    }

    private final NotificacaoRepository notificacaoRepository;
    private final UsuarioService usuarioService;

    @GetMapping("/admin/notificacoes")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<NotificacaoResponse>>> listar(
            @RequestParam(required = false) Boolean naoLidas,
            @RequestParam(required = false) TipoNotificacao tipo,
            @ParameterObject @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID usuario = usuarioService.obterUsuarioOnline().getId();
        Specification<Notificacao> naoLidasSpec = !Boolean.TRUE.equals(naoLidas) ? null : (root, query, cb) -> {
            var sub = query.subquery(Integer.class);
            var leitura = sub.from(NotificacaoLeitura.class);
            sub.select(cb.literal(1)).where(
                    cb.equal(leitura.get("id").get("notificacaoId"), root.get("id")),
                    cb.equal(leitura.get("id").get("usuarioId"), usuario));
            return cb.not(cb.exists(sub));
        };
        Specification<Notificacao> spec = BaseSpecification.of(Notificacao.class)
                .equals("tipo", tipo)
                .and(naoLidasSpec)
                .build();
        Page<Notificacao> pagina = notificacaoRepository.findAll(spec, pageable);
        Set<String> lidas = pagina.isEmpty() ? Set.of()
                : new HashSet<>(notificacaoRepository.lidasEntre(usuario, pagina.map(Notificacao::getId).getContent()));
        return ResponseEntity.ok(ApiResponse.sucesso(PageResponse.from(pagina.map(n -> new NotificacaoResponse(
                n.getId(), n.getTipo().name(), n.getTitulo(), n.getMensagem(), n.getLink(), n.getCriadoEm(),
                lidas.contains(n.getId().toString()))))));
    }

    @GetMapping("/admin/notificacoes/resumo")
    public ResponseEntity<ApiResponse<ResumoResponse>> resumo() {
        return ResponseEntity.ok(ApiResponse.sucesso(new ResumoResponse(
                notificacaoRepository.contarNaoLidas(usuarioService.obterUsuarioOnline().getId()))));
    }

    @PatchMapping("/admin/notificacoes/{id}/lida")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> marcarLida(@PathVariable UUID id) {
        notificacaoRepository.marcarLida(id, usuarioService.obterUsuarioOnline().getId());
        return ResponseEntity.ok(ApiResponse.sucesso(null));
    }

    @PatchMapping("/admin/notificacoes/lidas")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> marcarTodasLidas() {
        notificacaoRepository.marcarTodasLidas(usuarioService.obterUsuarioOnline().getId());
        return ResponseEntity.ok(ApiResponse.sucesso("Todas marcadas como lidas.", null));
    }
}
