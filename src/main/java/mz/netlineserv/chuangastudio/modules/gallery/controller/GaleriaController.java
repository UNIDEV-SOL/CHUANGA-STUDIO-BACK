package mz.netlineserv.chuangastudio.modules.gallery.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.common.ApiResponse;
import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import mz.netlineserv.chuangastudio.shared.exceptions.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.shared.exceptions.RegraNegocioException;
import mz.netlineserv.chuangastudio.modules.gallery.domain.GaleriaImagem;
import mz.netlineserv.chuangastudio.modules.gallery.domain.GaleriaItem;
import mz.netlineserv.chuangastudio.modules.notifications.domain.Notificacao;
import mz.netlineserv.chuangastudio.modules.users.domain.Usuario;
import mz.netlineserv.chuangastudio.modules.gallery.domain.EstadoGaleria;
import mz.netlineserv.chuangastudio.modules.notifications.domain.TipoNotificacao;
import mz.netlineserv.chuangastudio.modules.gallery.repository.GaleriaImagemRepository;
import mz.netlineserv.chuangastudio.modules.gallery.repository.GaleriaItemRepository;
import mz.netlineserv.chuangastudio.modules.notifications.repository.NotificacaoRepository;
import mz.netlineserv.chuangastudio.shared.persistence.BaseSpecification;
import mz.netlineserv.chuangastudio.modules.users.service.UsuarioService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Galeria de trabalhos: a equipa carrega e gere as fotos; as clientes podem sugerir fotos,
 * que ficam PENDENTE até a equipa aprovar (curadoria). O site só mostra APROVADA + publicada.
 */
@RestController
@RequiredArgsConstructor
public class GaleriaController {

    private static final Set<String> TIPOS = Set.of("image/jpeg", "image/png", "image/webp");

    public record GaleriaItemResponse(UUID id, String titulo, String descricao, String categoria, Integer largura,
                                      Integer altura, int ordem, boolean publicado, boolean destaque, String estado,
                                      String sugeridoPorNome, String motivoRejeicao,
                                      String imagemUrl, String miniaturaUrl, LocalDateTime criadoEm) {
        static GaleriaItemResponse de(GaleriaItem i) {
            String base = "/api/publico/galeria/" + i.getId() + "/imagem";
            // ?v= muda quando a foto é substituída, para o cache do browser não mostrar a antiga.
            String versao = "?v=" + i.getImagemId().toString().substring(0, 8);
            return new GaleriaItemResponse(i.getId(), i.getTitulo(), i.getDescricao(), i.getCategoria(), i.getLargura(),
                    i.getAltura(), i.getOrdem(), i.isPublicado(), i.isDestaque(), i.getEstado().name(),
                    i.getSugeridoPor() != null ? i.getSugeridoPor().getNome() : null, i.getMotivoRejeicao(),
                    base + versao, base + versao + "&tamanho=miniatura", i.getCriadoEm());
        }
    }

    public record AtualizarItemRequest(
            @Size(max = 120, message = "Título demasiado longo") String titulo,
            @Size(max = 400, message = "Descrição demasiado longa") String descricao,
            @Size(max = 80, message = "Categoria demasiado longa") String categoria,
            Integer ordem,
            Boolean publicado,
            Boolean destaque) {
    }

    public record RejeitarRequest(@Size(max = 200, message = "Motivo demasiado longo") String motivo) {
    }

    private final GaleriaItemRepository itemRepository;
    private final GaleriaImagemRepository imagemRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final UsuarioService usuarioService;

    // --- Site ---

    @GetMapping("/publico/galeria")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<GaleriaItemResponse>>> publica(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Boolean destaque,
            @ParameterObject @PageableDefault(size = 12, sort = {"ordem", "criadoEm"}, direction = Sort.Direction.DESC) Pageable pageable) {
        var spec = BaseSpecification.of(GaleriaItem.class)
                .equals("publicado", true)
                .equals("estado", EstadoGaleria.APROVADA)
                .equals("categoria", categoria)
                .equals("destaque", destaque)
                .build();
        return ResponseEntity.ok(ApiResponse.sucesso(PageResponse.from(itemRepository.findAll(spec, pageable).map(GaleriaItemResponse::de))));
    }

    @GetMapping("/publico/galeria/{id}/imagem")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> imagem(@PathVariable UUID id, @RequestParam(required = false) String tamanho) {
        GaleriaItem item = buscar(id);
        GaleriaImagem imagem = imagemRepository.findById(item.getImagemId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imagem não encontrada."));
        byte[] bytes = "miniatura".equals(tamanho) ? imagem.getMiniatura() : imagem.getDados();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imagem.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .body(bytes);
    }

    // --- Sugestões das clientes ---

    @PostMapping(value = "/minha-area/galeria/sugestoes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<ApiResponse<GaleriaItemResponse>> sugerir(
            @RequestPart("imagem") MultipartFile imagem,
            @RequestPart("miniatura") MultipartFile miniatura,
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String descricao,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Integer largura,
            @RequestParam(required = false) Integer altura) throws IOException {
        Usuario cliente = usuarioService.obterUsuarioOnline();
        GaleriaItem item = novoItem(imagem, miniatura, titulo, descricao, categoria, largura, altura);
        item.setEstado(EstadoGaleria.PENDENTE);
        item.setPublicado(false);
        item.setSugeridoPor(cliente);
        GaleriaItem salvo = itemRepository.save(item);

        Notificacao aviso = new Notificacao();
        aviso.setTipo(TipoNotificacao.GALERIA_SUGESTAO);
        aviso.setTitulo("Foto sugerida para a galeria · " + cliente.getNome());
        aviso.setMensagem(salvo.getTitulo() != null ? salvo.getTitulo() : "Aguarda aprovação");
        aviso.setLink("/admin/galeria?separador=sugestoes");
        aviso.setReferenciaId(salvo.getId());
        notificacaoRepository.save(aviso);

        return ResponseEntity.ok(ApiResponse.sucesso("Obrigada! A foto vai ser vista pela equipa antes de aparecer na galeria.",
                GaleriaItemResponse.de(salvo)));
    }

    @GetMapping("/minha-area/galeria/sugestoes")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<GaleriaItemResponse>>> minhasSugestoes(
            @ParameterObject @PageableDefault(size = 12, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        var spec = BaseSpecification.of(GaleriaItem.class)
                .equals("sugeridoPor.id", usuarioService.obterUsuarioOnline().getId())
                .build();
        return ResponseEntity.ok(ApiResponse.sucesso(PageResponse.from(itemRepository.findAll(spec, pageable).map(GaleriaItemResponse::de))));
    }

    // --- Painel ---

    @GetMapping("/admin/galeria")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<GaleriaItemResponse>>> listarAdmin(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Boolean publicado,
            @RequestParam(required = false) EstadoGaleria estado,
            @ParameterObject @PageableDefault(size = 24, sort = {"ordem", "criadoEm"}, direction = Sort.Direction.DESC) Pageable pageable) {
        var spec = BaseSpecification.of(GaleriaItem.class)
                .likeAny(busca, "titulo", "descricao")
                .equals("categoria", categoria)
                .equals("publicado", publicado)
                .equals("estado", estado)
                .build();
        return ResponseEntity.ok(ApiResponse.sucesso(PageResponse.from(itemRepository.findAll(spec, pageable).map(GaleriaItemResponse::de))));
    }

    /** Recebe a foto já reduzida pelo painel (grande + miniatura) com os dados do mini-formulário. */
    @PostMapping(value = "/admin/galeria", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<ApiResponse<GaleriaItemResponse>> carregar(
            @RequestPart("imagem") MultipartFile imagem,
            @RequestPart("miniatura") MultipartFile miniatura,
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String descricao,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Integer largura,
            @RequestParam(required = false) Integer altura,
            @RequestParam(defaultValue = "true") boolean publicado,
            @RequestParam(defaultValue = "false") boolean destaque) throws IOException {
        GaleriaItem item = novoItem(imagem, miniatura, titulo, descricao, categoria, largura, altura);
        item.setPublicado(publicado);
        item.setDestaque(destaque);
        return ResponseEntity.ok(ApiResponse.sucesso("Foto adicionada à galeria.", GaleriaItemResponse.de(itemRepository.save(item))));
    }

    /** Troca a foto mantendo título, categoria, ordem e estado. */
    @PutMapping(value = "/admin/galeria/{id}/imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<ApiResponse<GaleriaItemResponse>> substituirImagem(
            @PathVariable UUID id,
            @RequestPart("imagem") MultipartFile imagem,
            @RequestPart("miniatura") MultipartFile miniatura,
            @RequestParam(required = false) Integer largura,
            @RequestParam(required = false) Integer altura) throws IOException {
        GaleriaItem item = buscar(id);
        UUID antiga = item.getImagemId();
        item.setImagemId(guardarBytes(imagem, miniatura).getId());
        item.setLargura(largura);
        item.setAltura(altura);
        GaleriaItem salvo = itemRepository.saveAndFlush(item);
        imagemRepository.deleteById(antiga);
        return ResponseEntity.ok(ApiResponse.sucesso("Foto substituída.", GaleriaItemResponse.de(salvo)));
    }

    @PutMapping("/admin/galeria/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<GaleriaItemResponse>> atualizar(@PathVariable UUID id, @Valid @RequestBody AtualizarItemRequest request) {
        GaleriaItem item = buscar(id);
        item.setTitulo(texto(request.titulo()));
        item.setDescricao(texto(request.descricao()));
        item.setCategoria(texto(request.categoria()));
        if (request.ordem() != null) item.setOrdem(request.ordem());
        if (request.publicado() != null) item.setPublicado(request.publicado() && item.getEstado() == EstadoGaleria.APROVADA);
        if (request.destaque() != null) item.setDestaque(request.destaque());
        return ResponseEntity.ok(ApiResponse.sucesso("Foto atualizada.", GaleriaItemResponse.de(itemRepository.save(item))));
    }

    @PatchMapping("/admin/galeria/{id}/aprovar")
    @Transactional
    public ResponseEntity<ApiResponse<GaleriaItemResponse>> aprovar(@PathVariable UUID id) {
        GaleriaItem item = buscar(id);
        item.setEstado(EstadoGaleria.APROVADA);
        item.setPublicado(true);
        item.setMotivoRejeicao(null);
        return ResponseEntity.ok(ApiResponse.sucesso("Sugestão aprovada e publicada na galeria.", GaleriaItemResponse.de(itemRepository.save(item))));
    }

    @PatchMapping("/admin/galeria/{id}/rejeitar")
    @Transactional
    public ResponseEntity<ApiResponse<GaleriaItemResponse>> rejeitar(@PathVariable UUID id, @Valid @RequestBody RejeitarRequest request) {
        GaleriaItem item = buscar(id);
        item.setEstado(EstadoGaleria.REJEITADA);
        item.setPublicado(false);
        item.setMotivoRejeicao(texto(request.motivo()));
        return ResponseEntity.ok(ApiResponse.sucesso("Sugestão rejeitada.", GaleriaItemResponse.de(itemRepository.save(item))));
    }

    @DeleteMapping("/admin/galeria/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> remover(@PathVariable UUID id) {
        // Apaga mesmo (a foto deixa de ocupar espaço na base de dados); o item primeiro por causa da FK.
        GaleriaItem item = buscar(id);
        itemRepository.delete(item);
        itemRepository.flush();
        imagemRepository.deleteById(item.getImagemId());
        return ResponseEntity.ok(ApiResponse.sucesso("Foto removida.", null));
    }

    private GaleriaItem novoItem(MultipartFile imagem, MultipartFile miniatura, String titulo, String descricao,
                                 String categoria, Integer largura, Integer altura) throws IOException {
        GaleriaItem item = new GaleriaItem();
        item.setImagemId(guardarBytes(imagem, miniatura).getId());
        item.setTitulo(texto(titulo));
        item.setDescricao(texto(descricao));
        item.setCategoria(texto(categoria));
        item.setLargura(largura);
        item.setAltura(altura);
        return item;
    }

    private GaleriaImagem guardarBytes(MultipartFile imagem, MultipartFile miniatura) throws IOException {
        if (imagem.isEmpty() || !TIPOS.contains(imagem.getContentType())) {
            throw new RegraNegocioException("Envie uma imagem JPG, PNG ou WebP.");
        }
        GaleriaImagem bytes = new GaleriaImagem();
        bytes.setId(UUID.randomUUID());
        bytes.setContentType(imagem.getContentType());
        bytes.setDados(imagem.getBytes());
        bytes.setMiniatura(miniatura.isEmpty() ? imagem.getBytes() : miniatura.getBytes());
        return imagemRepository.save(bytes);
    }

    private GaleriaItem buscar(UUID id) {
        return itemRepository.findById(id).filter(i -> !i.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Foto não encontrada."));
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
