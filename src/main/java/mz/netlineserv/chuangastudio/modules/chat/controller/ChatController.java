package mz.netlineserv.chuangastudio.modules.chat.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.common.ApiResponse;
import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mz.netlineserv.chuangastudio.modules.chat.service.ChatService;
import mz.netlineserv.chuangastudio.modules.chat.service.ChatSuporte;
import mz.netlineserv.chuangastudio.modules.chat.service.GrupoService;
import mz.netlineserv.chuangastudio.modules.chat.service.TiposAnexo;

/** Chat para clientes e equipa (qualquer utilizador autenticado; os serviços decidem o acesso). */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    public record TextoRequest(@Size(max = 4000, message = "Mensagem demasiado longa (máx. 4000 caracteres)") String texto, UUID respostaA) {
    }

    public record ReacaoRequest(@Size(max = 16, message = "Reação inválida") String emoji) {
    }

    public record EncaminharRequest(@NotEmpty(message = "Escolha para onde reencaminhar") @Size(max = 20, message = "Pode reencaminhar para até 20 conversas de cada vez") List<UUID> conversas) {
    }

    public record FixarRequest(UUID mensagemId) {
    }

    public record MembrosRequest(@NotEmpty(message = "Escolha quem adicionar") @Size(max = 100, message = "Pode adicionar até 100 pessoas de cada vez") List<UUID> usuarios) {
    }

    public record DetalhesResponse(ChatSuporte.ConversaResponse conversa, GrupoService.Detalhes detalhes) {
    }

    private final ChatService chatService;
    private final GrupoService grupoService;

    // --- Conversas ---

    @GetMapping("/conversas")
    public ResponseEntity<ApiResponse<PageResponse<ChatSuporte.ConversaResponse>>> conversas(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false, defaultValue = "todas") String filtro,
            @ParameterObject @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.sucesso(chatService.conversas(busca, filtro, pageable)));
    }

    @GetMapping("/conversas/{id}")
    public ResponseEntity<ApiResponse<ChatSuporte.ConversaResponse>> conversa(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso(chatService.conversaPorId(id)));
    }

    @GetMapping("/conversas/{id}/detalhes")
    public ResponseEntity<ApiResponse<DetalhesResponse>> detalhes(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso(new DetalhesResponse(chatService.conversaPorId(id), grupoService.detalhes(id))));
    }

    @GetMapping("/nao-lidas")
    public ResponseEntity<ApiResponse<Map<String, Long>>> naoLidas() {
        return ResponseEntity.ok(ApiResponse.sucesso(Map.of("naoLidas", chatService.totalNaoLidas())));
    }

    @GetMapping("/contactos")
    public ResponseEntity<ApiResponse<List<GrupoService.Contacto>>> contactos(@RequestParam(required = false) String busca) {
        return ResponseEntity.ok(ApiResponse.sucesso(grupoService.contactos(busca)));
    }

    @GetMapping("/comunidade")
    public ResponseEntity<ApiResponse<List<GrupoService.ComunidadeItem>>> comunidade() {
        return ResponseEntity.ok(ApiResponse.sucesso(grupoService.comunidade()));
    }

    @PostMapping("/conversas/cliente/{clienteId}")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> conversaComCliente(@PathVariable UUID clienteId) {
        return ResponseEntity.ok(ApiResponse.sucesso(Map.of("id", grupoService.conversaComCliente(clienteId))));
    }

    /** Abre a conversa com uma pessoa (direta, ou a conversa com o estúdio). */
    @PostMapping("/conversas/com/{usuarioId}")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> abrirCom(@PathVariable UUID usuarioId) {
        return ResponseEntity.ok(ApiResponse.sucesso(Map.of("id", grupoService.abrirCom(usuarioId))));
    }

    @PutMapping("/conversas/{id}/lida")
    public ResponseEntity<ApiResponse<Void>> marcarLida(@PathVariable UUID id) {
        chatService.marcarLida(id);
        return ResponseEntity.ok(ApiResponse.sucesso(null));
    }

    @PatchMapping("/conversas/{id}/preferencias")
    public ResponseEntity<ApiResponse<Void>> preferencias(@PathVariable UUID id, @RequestBody ChatService.Preferencias corpo) {
        chatService.preferencias(id, corpo);
        return ResponseEntity.ok(ApiResponse.sucesso(null));
    }

    @PostMapping("/conversas/{id}/a-escrever")
    public ResponseEntity<ApiResponse<Void>> aEscrever(@PathVariable UUID id) {
        chatService.aEscrever(id);
        return ResponseEntity.ok(ApiResponse.sucesso(null));
    }

    @PutMapping("/conversas/{id}/fixada")
    public ResponseEntity<ApiResponse<Void>> fixar(@PathVariable UUID id, @RequestBody FixarRequest corpo) {
        chatService.fixar(id, corpo.mensagemId());
        return ResponseEntity.ok(ApiResponse.sucesso(null));
    }

    // --- Grupos ---

    @PostMapping("/grupos")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> criarGrupo(@RequestBody GrupoService.CriarGrupoRequest corpo) {
        return ResponseEntity.ok(ApiResponse.sucesso("Grupo criado.", Map.of("id", grupoService.criar(corpo))));
    }

    @PatchMapping("/grupos/{id}")
    public ResponseEntity<ApiResponse<Void>> atualizarGrupo(@PathVariable UUID id, @RequestBody GrupoService.AtualizarGrupoRequest corpo) {
        grupoService.atualizar(id, corpo);
        return ResponseEntity.ok(ApiResponse.sucesso("Grupo atualizado.", null));
    }

    @PutMapping(value = "/grupos/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> fotoGrupo(@PathVariable UUID id, @RequestPart("imagem") MultipartFile imagem) throws IOException {
        grupoService.foto(id, imagem);
        return ResponseEntity.ok(ApiResponse.sucesso("Foto do grupo atualizada.", null));
    }

    @GetMapping("/conversas/{id}/foto")
    public ResponseEntity<byte[]> foto(@PathVariable UUID id) {
        return TiposAnexo.servir(grupoService.fotoAnexo(id), TiposAnexo.cachePrivado(Duration.ofHours(1)));
    }

    @PostMapping("/grupos/{id}/membros")
    public ResponseEntity<ApiResponse<Void>> adicionar(@PathVariable UUID id, @Valid @RequestBody MembrosRequest corpo) {
        grupoService.adicionar(id, corpo.usuarios());
        return ResponseEntity.ok(ApiResponse.sucesso("Adicionadas ao grupo.", null));
    }

    @DeleteMapping("/grupos/{id}/membros/{usuarioId}")
    public ResponseEntity<ApiResponse<Void>> remover(@PathVariable UUID id, @PathVariable UUID usuarioId) {
        grupoService.remover(id, usuarioId);
        return ResponseEntity.ok(ApiResponse.sucesso("Removida do grupo.", null));
    }

    @PatchMapping("/grupos/{id}/membros/{usuarioId}")
    public ResponseEntity<ApiResponse<Void>> admin(@PathVariable UUID id, @PathVariable UUID usuarioId, @RequestBody Map<String, Boolean> corpo) {
        boolean admin = Boolean.TRUE.equals(corpo.get("admin"));
        grupoService.definirAdmin(id, usuarioId, admin);
        return ResponseEntity.ok(ApiResponse.sucesso(admin ? "Agora é administradora." : "Deixou de ser administradora.", null));
    }

    @PostMapping("/conversas/{id}/sair")
    public ResponseEntity<ApiResponse<Void>> sair(@PathVariable UUID id) {
        grupoService.sair(id);
        return ResponseEntity.ok(ApiResponse.sucesso("Saiu da conversa.", null));
    }

    @PostMapping("/grupos/{id}/entrar")
    public ResponseEntity<ApiResponse<Void>> entrar(@PathVariable UUID id) {
        grupoService.entrar(id);
        return ResponseEntity.ok(ApiResponse.sucesso("Entrou no grupo.", null));
    }

    @DeleteMapping("/grupos/{id}")
    public ResponseEntity<ApiResponse<Void>> apagarGrupo(@PathVariable UUID id) {
        grupoService.apagar(id);
        return ResponseEntity.ok(ApiResponse.sucesso("Grupo apagado.", null));
    }

    // --- Mensagens ---

    /** Histórico (as mais recentes, ou antes de um instante ao subir; ou a partir de uma mensagem). */
    @GetMapping("/conversas/{id}/mensagens")
    public ResponseEntity<ApiResponse<ChatService.Historico>> mensagens(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime antes,
            @RequestParam(required = false) UUID aPartirDe) {
        return ResponseEntity.ok(ApiResponse.sucesso(aPartirDe != null ? chatService.aPartirDe(id, aPartirDe) : chatService.historico(id, antes)));
    }

    /** Polling incremental: o ecrã envia o último cursor e recebe só o que mudou. */
    @GetMapping("/conversas/{id}/sincronizar")
    public ResponseEntity<ApiResponse<ChatService.Historico>> sincronizar(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde) {
        return ResponseEntity.ok(ApiResponse.sucesso(chatService.sincronizar(id, desde)));
    }

    @GetMapping("/conversas/{id}/pesquisar")
    public ResponseEntity<ApiResponse<PageResponse<ChatSuporte.MensagemResponse>>> pesquisar(
            @PathVariable UUID id, @RequestParam @Size(max = 200, message = "Pesquisa demasiado longa (máx. 200 caracteres)") String q, @ParameterObject @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.sucesso(chatService.pesquisar(id, q, pageable)));
    }

    @GetMapping("/conversas/{id}/midia")
    public ResponseEntity<ApiResponse<PageResponse<ChatSuporte.MensagemResponse>>> midia(
            @PathVariable UUID id, @RequestParam(defaultValue = "IMAGEM") String tipo,
            @ParameterObject @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.sucesso(chatService.midia(id, tipo, pageable)));
    }

    @GetMapping("/favoritas")
    public ResponseEntity<ApiResponse<List<ChatService.FavoritaResponse>>> favoritas(@RequestParam(required = false) UUID conversa) {
        return ResponseEntity.ok(ApiResponse.sucesso(chatService.favoritas(conversa)));
    }

    @PostMapping("/conversas/{id}/mensagens")
    public ResponseEntity<ApiResponse<ChatSuporte.MensagemResponse>> enviar(@PathVariable UUID id, @Valid @RequestBody TextoRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso(chatService.enviarTexto(id, request.texto(), request.respostaA())));
    }

    @PostMapping(value = "/conversas/{id}/imagens", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ChatSuporte.MensagemResponse>> enviarImagem(
            @PathVariable UUID id,
            @RequestPart("imagem") MultipartFile imagem,
            @RequestParam(required = false) @Size(max = 4000, message = "Legenda demasiado longa (máx. 4000 caracteres)") String legenda,
            @RequestParam(required = false) UUID respostaA) throws IOException {
        return ResponseEntity.ok(ApiResponse.sucesso(chatService.enviarImagem(id, imagem, legenda, respostaA)));
    }

    /** Documento ou mensagem de voz (voz=true, com a duração em segundos). */
    @PostMapping(value = "/conversas/{id}/ficheiros", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ChatSuporte.MensagemResponse>> enviarFicheiro(
            @PathVariable UUID id,
            @RequestPart("ficheiro") MultipartFile ficheiro,
            @RequestParam(defaultValue = "false") boolean voz,
            @RequestParam(required = false) Integer duracao,
            @RequestParam(required = false) UUID respostaA) throws IOException {
        return ResponseEntity.ok(ApiResponse.sucesso(chatService.enviarFicheiro(id, ficheiro, voz, duracao, respostaA)));
    }

    @PutMapping("/mensagens/{id}")
    public ResponseEntity<ApiResponse<ChatSuporte.MensagemResponse>> editar(@PathVariable UUID id, @Valid @RequestBody TextoRequest request) {
        return ResponseEntity.ok(ApiResponse.sucesso(chatService.editar(id, request.texto())));
    }

    @DeleteMapping("/mensagens/{id}")
    public ResponseEntity<ApiResponse<Void>> apagar(@PathVariable UUID id, @RequestParam(defaultValue = "true") boolean paraTodos) {
        chatService.apagar(id, paraTodos);
        return ResponseEntity.ok(ApiResponse.sucesso(null));
    }

    @PutMapping("/mensagens/{id}/reacao")
    public ResponseEntity<ApiResponse<ChatSuporte.MensagemResponse>> reagir(@PathVariable UUID id, @Valid @RequestBody ReacaoRequest corpo) {
        return ResponseEntity.ok(ApiResponse.sucesso(chatService.reagir(id, corpo.emoji())));
    }

    @PutMapping("/mensagens/{id}/favorita")
    public ResponseEntity<ApiResponse<ChatSuporte.MensagemResponse>> favorita(@PathVariable UUID id, @RequestBody Map<String, Boolean> corpo) {
        return ResponseEntity.ok(ApiResponse.sucesso(chatService.favorita(id, Boolean.TRUE.equals(corpo.get("favorita")))));
    }

    @PostMapping("/mensagens/{id}/encaminhar")
    public ResponseEntity<ApiResponse<Void>> encaminhar(@PathVariable UUID id, @Valid @RequestBody EncaminharRequest corpo) {
        int n = chatService.encaminhar(id, corpo.conversas());
        return ResponseEntity.ok(ApiResponse.sucesso(n == 1 ? "Mensagem reencaminhada." : "Mensagem reencaminhada para " + n + " conversas.", null));
    }

    @GetMapping("/mensagens/{id}/info")
    public ResponseEntity<ApiResponse<ChatService.InfoMensagem>> info(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.sucesso(chatService.info(id)));
    }

    /** Ficheiro da mensagem (foto, documento ou voz). */
    @GetMapping({"/mensagens/{id}/anexo", "/mensagens/{id}/imagem"})
    public ResponseEntity<byte[]> anexo(@PathVariable UUID id) {
        return TiposAnexo.servir(chatService.anexo(id), TiposAnexo.cachePrivado(Duration.ofDays(7)));
    }
}
