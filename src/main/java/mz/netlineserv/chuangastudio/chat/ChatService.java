package mz.netlineserv.chuangastudio.chat;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.PageResponse;
import mz.netlineserv.chuangastudio.common.exception.AcessoNegadoException;
import mz.netlineserv.chuangastudio.common.exception.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.repository.spec.BaseSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static mz.netlineserv.chuangastudio.chat.ChatSuporte.*;

/**
 * Chat ao estilo WhatsApp (sem chamadas):
 *  - lista de conversas com não lidas, fixadas, arquivadas e silenciadas por pessoa;
 *  - mensagens de texto, foto, documento e voz, com resposta, reações, reencaminhar, favoritas,
 *    editar (15 min), apagar para mim / para todos (2 dias), fixar e pesquisar;
 *  - sincronização incremental de 3 em 3 s com vistos (✓✓), "a escrever..." e "online".
 * A gestão dos grupos e da comunidade está no {@link GrupoService}.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int TAMANHO_HISTORICO = 40;
    /** Margem na sincronização para não perder mensagens de transações que acabaram de fazer commit. */
    private static final Duration SOBREPOSICAO = Duration.ofSeconds(5);
    private static final long MAX_FICHEIRO = 10L * 1024 * 1024;
    private static final Set<String> EMOJIS = Set.of("👍", "❤️", "😂", "😮", "😢", "🙏", "💅", "🔥", "👏", "😍");

    private final ChatSuporte suporte;
    private final ConversaRepository conversaRepository;
    private final ConversaMembroRepository membroRepository;
    private final MensagemRepository mensagemRepository;
    private final ChatAnexoRepository anexoRepository;
    private final GrupoService grupoService;

    public record Historico(List<MensagemResponse> mensagens, boolean temMais, LocalDateTime cursor,
                            LocalDateTime lidaPorTodosAte, List<String> aEscrever, boolean online, LocalDateTime vistoEm) {
    }

    public record LeituraResponse(UUID usuarioId, String nome, LocalDateTime lidaEm) {
    }

    public record InfoMensagem(List<LeituraResponse> lidaPor, List<String> porLer) {
    }

    public record FavoritaResponse(MensagemResponse mensagem, String conversaTitulo) {
    }

    public record Preferencias(Boolean fixada, Boolean arquivada, Boolean silenciada) {
    }

    // --- Conversas ---

    /** filtro: todas | nao-lidas | grupos | arquivadas */
    @Transactional
    public PageResponse<ConversaResponse> conversas(String busca, String filtro, Pageable pageable) {
        Usuario eu = suporte.eu();
        grupoService.garantirComunidade(eu);
        List<UUID> minhas = membroRepository.conversasDe(eu.getId());
        Set<UUID> arquivadas = conversaRepository.arquivadas(eu.getId()).stream().map(UUID::fromString).collect(Collectors.toSet());
        Set<UUID> fixadas = conversaRepository.fixadas(eu.getId()).stream().map(UUID::fromString).collect(Collectors.toSet());

        Specification<Conversa> visiveis = visiveis(eu, minhas);
        BaseSpecification<Conversa> spec = BaseSpecification.of(Conversa.class).and(visiveis);
        if ("arquivadas".equals(filtro)) {
            spec.in("id", arquivadas.isEmpty() ? List.of(new UUID(0, 0)) : arquivadas);
        } else if (!arquivadas.isEmpty()) {
            spec.and((root, q, cb) -> cb.not(root.get("id").in(arquivadas)));
        }
        if ("grupos".equals(filtro)) {
            spec.equals("tipo", Conversa.Tipo.GRUPO);
        }
        if ("nao-lidas".equals(filtro)) {
            List<UUID> todas = conversaRepository.findAll(visiveis).stream().map(Conversa::getId).toList();
            Set<UUID> comNaoLidas = naoLidas(eu, todas).entrySet().stream().filter(e -> e.getValue() > 0)
                    .map(Map.Entry::getKey).collect(Collectors.toSet());
            spec.in("id", comNaoLidas.isEmpty() ? List.of(new UUID(0, 0)) : comNaoLidas);
        }
        if (busca != null && !busca.isBlank()) {
            String padrao = "%" + busca.trim().toLowerCase() + "%";
            // Nas diretas o título guardado não serve (cada uma vê o nome da outra): procura pelos membros.
            spec.and((root, q, cb) -> {
                var sub = q.subquery(UUID.class);
                var mm = sub.from(ConversaMembro.class);
                var pessoa = mm.join("pessoa");
                sub.select(mm.get("conversa")).where(cb.like(cb.lower(pessoa.get("nome")), padrao), cb.notEqual(mm.get("usuario"), eu.getId()));
                return cb.or(cb.like(cb.lower(root.get("titulo")), padrao), root.get("id").in(sub));
            });
        }
        // Fixadas primeiro; depois as mais recentes (sem mensagens contam pela criação).
        Specification<Conversa> ordem = (root, query, cb) -> {
            if (!Long.class.equals(query.getResultType())) {
                List<jakarta.persistence.criteria.Order> ordens = new ArrayList<>();
                if (!fixadas.isEmpty()) {
                    ordens.add(cb.desc(cb.selectCase().when(root.get("id").in(fixadas), 1).otherwise(0)));
                }
                // Conversas ainda sem mensagens (ex. grupos de serviço acabados de criar) vão para o fim.
                ordens.add(cb.asc(cb.selectCase().when(cb.isNull(root.get("ultimaMensagemEm")), 1).otherwise(0)));
                ordens.add(cb.desc(cb.coalesce(root.get("ultimaMensagemEm"), root.get("criadoEm"))));
                query.orderBy(ordens);
            }
            return null;
        };
        Page<Conversa> pagina = conversaRepository.findAll(spec.build().and(ordem),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        return PageResponse.from(new org.springframework.data.domain.PageImpl<>(
                respostas(pagina.getContent(), eu), pagina.getPageable(), pagina.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public ConversaResponse conversaPorId(UUID id) {
        Usuario eu = suporte.eu();
        Conversa c = suporte.conversa(id);
        suporte.exigirVer(c, eu);
        return respostas(List.of(c), eu).get(0);
    }

    /** Total por ler (para o contador no menu); as silenciadas não contam. */
    @Transactional(readOnly = true)
    public long totalNaoLidas() {
        Usuario eu = suporte.eu();
        List<UUID> ids = conversaRepository.findAll(visiveis(eu, membroRepository.conversasDe(eu.getId())))
                .stream().map(Conversa::getId).toList();
        if (ids.isEmpty()) return 0;
        Set<String> silenciadas = conversaRepository.preferencias(eu.getId(), ids).stream()
                .filter(r -> Boolean.TRUE.equals(r[3])).map(r -> (String) r[0]).collect(Collectors.toSet());
        return naoLidas(eu, ids).entrySet().stream().filter(e -> !silenciadas.contains(e.getKey().toString()))
                .mapToLong(Map.Entry::getValue).sum();
    }

    @Transactional
    public void marcarLida(UUID conversaId) {
        Usuario eu = suporte.eu();
        suporte.exigirVer(suporte.conversa(conversaId), eu);
        conversaRepository.marcarLida(conversaId, eu.getId(), LocalDateTime.now());
    }

    @Transactional
    public void preferencias(UUID conversaId, Preferencias p) {
        Usuario eu = suporte.eu();
        suporte.exigirVer(suporte.conversa(conversaId), eu);
        conversaRepository.guardarPreferencias(conversaId, eu.getId(), p.fixada(), p.arquivada(), p.silenciada());
    }

    @Transactional(readOnly = true)
    public void aEscrever(UUID conversaId) {
        Usuario eu = suporte.eu();
        suporte.exigirVer(suporte.conversa(conversaId), eu);
        suporte.aEscrever(conversaId, eu);
    }

    // --- Mensagens ---

    /** Histórico paginado para trás: antes = nulo devolve as mais recentes. */
    @Transactional(readOnly = true)
    public Historico historico(UUID conversaId, LocalDateTime antes) {
        Usuario eu = suporte.eu();
        Conversa c = suporte.conversa(conversaId);
        suporte.exigirVer(c, eu);
        LocalDateTime agora = LocalDateTime.now();
        List<Mensagem> desc = mensagemRepository.anteriores(conversaId, antes != null ? antes : agora.plusSeconds(1),
                PageRequest.of(0, TAMANHO_HISTORICO + 1));
        boolean temMais = desc.size() > TAMANHO_HISTORICO;
        List<Mensagem> pagina = new ArrayList<>(desc.subList(0, Math.min(desc.size(), TAMANHO_HISTORICO)));
        Collections.reverse(pagina);
        return historico(c, eu, suporte.paraResposta(pagina, eu), temMais, agora);
    }

    /** Para saltar para uma mensagem antiga (pesquisa, resposta, fixada): tudo desde ela até agora. */
    @Transactional(readOnly = true)
    public Historico aPartirDe(UUID conversaId, UUID mensagemId) {
        Usuario eu = suporte.eu();
        Conversa c = suporte.conversa(conversaId);
        suporte.exigirVer(c, eu);
        Mensagem alvo = suporte.mensagem(mensagemId);
        if (!alvo.getConversa().getId().equals(conversaId)) {
            throw new RecursoNaoEncontradoException("Mensagem não encontrada nesta conversa.");
        }
        LocalDateTime agora = LocalDateTime.now();
        List<Mensagem> desde = mensagemRepository.aPartirDe(conversaId, alvo.getCriadoEm().minusSeconds(1), PageRequest.of(0, 1000));
        List<Mensagem> antes = mensagemRepository.anteriores(conversaId, alvo.getCriadoEm().minusSeconds(1), PageRequest.of(0, 11));
        boolean temMais = antes.size() > 10;
        List<Mensagem> todas = new ArrayList<>(antes.subList(0, Math.min(antes.size(), 10)));
        Collections.reverse(todas);
        todas.addAll(desde);
        return historico(c, eu, suporte.paraResposta(todas, eu), temMais, agora);
    }

    /** O que mudou desde o último cursor (novas, editadas, apagadas, reações), mais vistos e presença. */
    @Transactional(readOnly = true)
    public Historico sincronizar(UUID conversaId, LocalDateTime desde) {
        Usuario eu = suporte.eu();
        Conversa c = suporte.conversa(conversaId);
        suporte.exigirVer(c, eu);
        LocalDateTime agora = LocalDateTime.now();
        List<Mensagem> mudadas = mensagemRepository.alteradasDesde(conversaId, desde.minus(SOBREPOSICAO));
        return historico(c, eu, suporte.paraResposta(mudadas, eu), false, agora);
    }

    private Historico historico(Conversa c, Usuario eu, List<MensagemResponse> mensagens, boolean temMais, LocalDateTime cursor) {
        Map<UUID, LocalDateTime> leituras = leituras(c);
        LocalDateTime lidaPorTodos = null;
        boolean online = false;
        LocalDateTime visto = null;
        switch (c.getTipo()) {
            case PRIVADA -> {
                if (eEquipa(eu)) {
                    UUID cliente = c.getCliente() != null ? c.getCliente().getId() : null;
                    lidaPorTodos = cliente != null ? leituras.get(cliente) : null;
                    online = cliente != null && suporte.online(cliente);
                    visto = cliente != null ? suporte.vistoEm(cliente) : null;
                } else {
                    // Para a cliente, "a equipa" leu quando qualquer pessoa da equipa leu.
                    List<UUID> equipa = suporte.equipa().stream().map(Usuario::getId).toList();
                    lidaPorTodos = equipa.stream().map(leituras::get).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
                    online = equipa.stream().anyMatch(suporte::online);
                    visto = equipa.stream().map(suporte::vistoEm).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
                }
            }
            case DIRETA -> {
                UUID outra = outroMembro(c, eu);
                if (outra != null) {
                    lidaPorTodos = leituras.get(outra);
                    online = suporte.online(outra);
                    visto = suporte.vistoEm(outra);
                }
            }
            case GRUPO -> {
                List<UUID> outras = membroRepository.ativos(c.getId()).stream().map(ConversaMembro::getUsuario)
                        .filter(id -> !id.equals(eu.getId())).toList();
                // Visto por todas: a leitura mais atrasada (quem nunca abriu ainda não leu).
                if (!outras.isEmpty() && outras.stream().allMatch(leituras::containsKey)) {
                    lidaPorTodos = outras.stream().map(leituras::get).min(Comparator.naturalOrder()).orElse(null);
                }
            }
        }
        return new Historico(mensagens, temMais, cursor, lidaPorTodos, suporte.quemEscreve(c.getId(), eu.getId()), online, visto);
    }

    @Transactional
    public MensagemResponse enviarTexto(UUID conversaId, String texto, UUID respostaA) {
        if (texto == null || texto.isBlank()) {
            throw new RegraNegocioException("Escreva uma mensagem.");
        }
        return enviar(conversaId, Mensagem.Tipo.TEXTO, texto.trim(), null, respostaA, null);
    }

    @Transactional
    public MensagemResponse enviarImagem(UUID conversaId, MultipartFile imagem, String legenda, UUID respostaA) throws IOException {
        if (imagem.isEmpty() || imagem.getContentType() == null || !imagem.getContentType().startsWith("image/")) {
            throw new RegraNegocioException("Envie uma imagem.");
        }
        UUID anexo = guardarAnexo(imagem, null);
        return enviar(conversaId, Mensagem.Tipo.IMAGEM, vazioParaNulo(legenda), anexo, respostaA, null);
    }

    /** Documento (PDF, Word...) ou mensagem de voz gravada no navegador. */
    @Transactional
    public MensagemResponse enviarFicheiro(UUID conversaId, MultipartFile ficheiro, boolean voz, Integer duracao, UUID respostaA) throws IOException {
        if (ficheiro.isEmpty()) {
            throw new RegraNegocioException("O ficheiro está vazio.");
        }
        String tipo = ficheiro.getContentType() == null ? "application/octet-stream" : ficheiro.getContentType();
        if (voz && !tipo.startsWith("audio/")) {
            throw new RegraNegocioException("A mensagem de voz tem de ser áudio.");
        }
        String nome = ficheiro.getOriginalFilename() == null ? "documento" : ficheiro.getOriginalFilename();
        UUID anexo = guardarAnexo(ficheiro, nome.length() > 200 ? nome.substring(nome.length() - 200) : nome);
        return voz
                ? enviar(conversaId, Mensagem.Tipo.AUDIO, null, anexo, respostaA, duracao)
                : enviar(conversaId, Mensagem.Tipo.DOCUMENTO, nome, anexo, respostaA, null);
    }

    private UUID guardarAnexo(MultipartFile f, String nome) throws IOException {
        if (f.getSize() > MAX_FICHEIRO) {
            throw new RegraNegocioException("O ficheiro pode ter até 10 MB.");
        }
        ChatAnexo anexo = new ChatAnexo();
        anexo.setId(UUID.randomUUID());
        String tipo = f.getContentType() == null ? "application/octet-stream" : f.getContentType();
        anexo.setContentType(tipo.length() > 120 ? tipo.substring(0, 120) : tipo);
        anexo.setNome(nome);
        anexo.setTamanho(f.getSize());
        anexo.setDados(f.getBytes());
        anexoRepository.save(anexo);
        return anexo.getId();
    }

    private MensagemResponse enviar(UUID conversaId, Mensagem.Tipo tipo, String conteudo, UUID anexoId, UUID respostaA, Integer duracao) {
        Usuario eu = suporte.eu();
        Conversa c = suporte.conversa(conversaId);
        suporte.exigirPodeEscrever(c, eu);
        if (respostaA != null && !suporte.mensagem(respostaA).getConversa().getId().equals(conversaId)) {
            throw new RegraNegocioException("Só pode responder a mensagens desta conversa.");
        }
        suporte.garantirEquipaNoGrupo(c, eu);
        Mensagem salva = suporte.gravar(c, eu, tipo, conteudo, anexoId, respostaA, duracao, false);
        return suporte.paraResposta(List.of(salva), eu).get(0);
    }

    @Transactional
    public MensagemResponse editar(UUID mensagemId, String texto) {
        Usuario eu = suporte.eu();
        Mensagem m = suporte.mensagem(mensagemId);
        if (!m.getRemetente().getId().equals(eu.getId()) || m.isApagada() || m.getTipo() != Mensagem.Tipo.TEXTO) {
            throw new AcessoNegadoException("Só pode editar as suas mensagens de texto.");
        }
        if (LocalDateTime.now().isAfter(m.getCriadoEm().plus(ChatSuporte.JANELA_EDICAO))) {
            throw new RegraNegocioException("Só é possível editar nos primeiros 15 minutos.");
        }
        if (texto == null || texto.isBlank()) {
            throw new RegraNegocioException("A mensagem não pode ficar vazia.");
        }
        m.setConteudo(texto.trim());
        m.setEditadaEm(LocalDateTime.now());
        return suporte.paraResposta(List.of(mensagemRepository.save(m)), eu).get(0);
    }

    /**
     * paraTodos: a autora até 2 dias depois; as administradoras de um grupo a qualquer momento (moderação).
     * Sem paraTodos: some só para quem apaga.
     */
    @Transactional
    public void apagar(UUID mensagemId, boolean paraTodos) {
        Usuario eu = suporte.eu();
        Mensagem m = suporte.mensagem(mensagemId);
        Conversa c = m.getConversa();
        suporte.exigirVer(c, eu);
        if (!paraTodos) {
            mensagemRepository.ocultar(m.getId(), eu.getId());
            return;
        }
        boolean minha = m.getRemetente().getId().equals(eu.getId());
        boolean moderacao = suporte.souAdmin(c, eu);
        if (!minha && !moderacao) {
            throw new AcessoNegadoException("Só pode apagar para todos as suas mensagens.");
        }
        if (minha && !moderacao && LocalDateTime.now().isAfter(m.getCriadoEm().plus(ChatSuporte.JANELA_APAGAR))) {
            throw new RegraNegocioException("Já passaram mais de 2 dias: pode apagar só para si.");
        }
        m.setApagada(true);
        m.setConteudo(null);
        mensagemRepository.save(m);
        if (m.getId().equals(c.getMensagemFixadaId())) {
            c.setMensagemFixadaId(null);
            conversaRepository.save(c);
        }
    }

    @Transactional
    public MensagemResponse reagir(UUID mensagemId, String emoji) {
        Usuario eu = suporte.eu();
        Mensagem m = suporte.mensagem(mensagemId);
        suporte.exigirVer(m.getConversa(), eu);
        if (m.isApagada() || m.getTipo() == Mensagem.Tipo.SISTEMA) {
            throw new RegraNegocioException("Não é possível reagir a esta mensagem.");
        }
        if (emoji == null || emoji.isBlank()) {
            mensagemRepository.retirarReacao(m.getId(), eu.getId());
        } else {
            if (!EMOJIS.contains(emoji)) throw new RegraNegocioException("Reação inválida.");
            mensagemRepository.reagir(m.getId(), eu.getId(), emoji);
        }
        mensagemRepository.tocar(m.getId());
        return suporte.paraResposta(List.of(m), eu).get(0);
    }

    @Transactional
    public MensagemResponse favorita(UUID mensagemId, boolean favorita) {
        Usuario eu = suporte.eu();
        Mensagem m = suporte.mensagem(mensagemId);
        suporte.exigirVer(m.getConversa(), eu);
        if (favorita) mensagemRepository.favoritar(m.getId(), eu.getId());
        else mensagemRepository.desfavoritar(m.getId(), eu.getId());
        return suporte.paraResposta(List.of(m), eu).get(0);
    }

    @Transactional(readOnly = true)
    public List<FavoritaResponse> favoritas(UUID conversaId) {
        Usuario eu = suporte.eu();
        List<UUID> ids = mensagemRepository.idsFavoritas(eu.getId()).stream().map(UUID::fromString).toList();
        if (ids.isEmpty()) return List.of();
        Map<UUID, Mensagem> porId = mensagemRepository.porIds(ids).stream().collect(Collectors.toMap(Mensagem::getId, x -> x));
        List<Mensagem> ordenadas = ids.stream().map(porId::get).filter(Objects::nonNull)
                .filter(m -> conversaId == null || m.getConversa().getId().equals(conversaId))
                .filter(m -> suporte.podeVer(m.getConversa(), eu)).toList();
        Map<UUID, ConversaResponse> conversas = respostas(ordenadas.stream().map(Mensagem::getConversa).distinct().toList(), eu)
                .stream().collect(Collectors.toMap(ConversaResponse::id, x -> x));
        List<FavoritaResponse> r = new ArrayList<>();
        for (Mensagem m : ordenadas) {
            suporte.paraResposta(List.of(m), eu).forEach(mr -> r.add(new FavoritaResponse(mr, conversas.get(m.getConversa().getId()).titulo())));
        }
        return r;
    }

    @Transactional
    public int encaminhar(UUID mensagemId, List<UUID> conversaIds) {
        Usuario eu = suporte.eu();
        Mensagem m = suporte.mensagem(mensagemId);
        suporte.exigirVer(m.getConversa(), eu);
        if (m.isApagada() || m.getTipo() == Mensagem.Tipo.SISTEMA) {
            throw new RegraNegocioException("Esta mensagem não pode ser reencaminhada.");
        }
        if (conversaIds == null || conversaIds.isEmpty() || conversaIds.size() > 5) {
            throw new RegraNegocioException("Escolha de 1 a 5 conversas.");
        }
        for (UUID id : new LinkedHashSet<>(conversaIds)) {
            Conversa destino = suporte.conversa(id);
            suporte.exigirPodeEscrever(destino, eu);
            suporte.garantirEquipaNoGrupo(destino, eu);
            suporte.gravar(destino, eu, m.getTipo(), m.getConteudo(), m.getAnexoId(), null, m.getDuracaoSegundos(), true);
        }
        return conversaIds.size();
    }

    /** Fixar no topo da conversa (nos grupos, só as administradoras). mensagemId nulo = desafixar. */
    @Transactional
    public void fixar(UUID conversaId, UUID mensagemId) {
        Usuario eu = suporte.eu();
        Conversa c = suporte.conversa(conversaId);
        suporte.exigirVer(c, eu);
        if (c.getTipo() == Conversa.Tipo.GRUPO) suporte.exigirAdmin(c, eu);
        if (mensagemId != null) {
            Mensagem m = suporte.mensagem(mensagemId);
            if (!m.getConversa().getId().equals(conversaId) || m.isApagada() || m.getTipo() == Mensagem.Tipo.SISTEMA) {
                throw new RegraNegocioException("Esta mensagem não pode ser fixada.");
            }
        }
        c.setMensagemFixadaId(mensagemId);
        conversaRepository.save(c);
        suporte.sistema(c, eu, primeiroNome(eu.getNome()) + (mensagemId != null ? " fixou uma mensagem" : " desafixou a mensagem"));
    }

    @Transactional(readOnly = true)
    public InfoMensagem info(UUID mensagemId) {
        Usuario eu = suporte.eu();
        Mensagem m = suporte.mensagem(mensagemId);
        Conversa c = m.getConversa();
        suporte.exigirVer(c, eu);
        if (!m.getRemetente().getId().equals(eu.getId())) {
            throw new AcessoNegadoException("Só pode ver os detalhes das suas mensagens.");
        }
        Map<UUID, LocalDateTime> leituras = leituras(c);
        List<LeituraResponse> lidas = new ArrayList<>();
        List<String> porLer = new ArrayList<>();
        for (Usuario u : suporte.participantes(c)) {
            if (u.getId().equals(eu.getId())) continue;
            LocalDateTime l = leituras.get(u.getId());
            if (l != null && !l.isBefore(m.getCriadoEm())) lidas.add(new LeituraResponse(u.getId(), u.getNome(), l));
            else if (c.getTipo() != Conversa.Tipo.PRIVADA || !eEquipa(u)) porLer.add(u.getNome());
        }
        lidas.sort(Comparator.comparing(LeituraResponse::lidaEm));
        return new InfoMensagem(lidas, porLer);
    }

    @Transactional(readOnly = true)
    public PageResponse<MensagemResponse> pesquisar(UUID conversaId, String q, Pageable pageable) {
        Usuario eu = suporte.eu();
        Conversa c = suporte.conversa(conversaId);
        suporte.exigirVer(c, eu);
        if (q == null || q.trim().length() < 2) {
            throw new RegraNegocioException("Escreva pelo menos 2 letras.");
        }
        Page<Mensagem> p = mensagemRepository.pesquisar(conversaId, q.trim(), pageable);
        return PageResponse.from(new org.springframework.data.domain.PageImpl<>(suporte.paraResposta(p.getContent(), eu), p.getPageable(), p.getTotalElements()));
    }

    /** Fotos (tipo=IMAGEM) ou documentos e voz (tipo=DOCUMENTO) partilhados na conversa. */
    @Transactional(readOnly = true)
    public PageResponse<MensagemResponse> midia(UUID conversaId, String tipo, Pageable pageable) {
        Usuario eu = suporte.eu();
        Conversa c = suporte.conversa(conversaId);
        suporte.exigirVer(c, eu);
        List<Mensagem.Tipo> tipos = "DOCUMENTO".equalsIgnoreCase(tipo)
                ? List.of(Mensagem.Tipo.DOCUMENTO, Mensagem.Tipo.AUDIO) : List.of(Mensagem.Tipo.IMAGEM);
        Page<Mensagem> p = mensagemRepository.porTipo(conversaId, tipos, pageable);
        return PageResponse.from(new org.springframework.data.domain.PageImpl<>(suporte.paraResposta(p.getContent(), eu), p.getPageable(), p.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public ChatAnexo anexo(UUID mensagemId) {
        Usuario eu = suporte.eu();
        Mensagem m = suporte.mensagem(mensagemId);
        suporte.exigirVer(m.getConversa(), eu);
        if (m.getAnexoId() == null || m.isApagada()) {
            throw new RecursoNaoEncontradoException("Ficheiro não encontrado.");
        }
        return anexoRepository.findById(m.getAnexoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ficheiro não encontrado."));
    }

    // --- Auxiliares ---

    /** A equipa vê as privadas com mensagens e todos os grupos; cada pessoa vê as suas diretas e grupos. */
    private Specification<Conversa> visiveis(Usuario eu, List<UUID> minhas) {
        Collection<UUID> membro = minhas.isEmpty() ? List.of(new UUID(0, 0)) : minhas;
        if (eEquipa(eu)) {
            return (root, q, cb) -> cb.or(
                    cb.and(cb.equal(root.get("tipo"), Conversa.Tipo.PRIVADA), cb.isNotNull(root.get("ultimaMensagemEm"))),
                    cb.equal(root.get("tipo"), Conversa.Tipo.GRUPO),
                    root.get("id").in(membro));
        }
        return (root, q, cb) -> cb.or(
                cb.and(cb.equal(root.get("tipo"), Conversa.Tipo.PRIVADA), cb.equal(root.get("cliente").get("id"), eu.getId())),
                root.get("id").in(membro));
    }

    List<ConversaResponse> respostas(List<Conversa> conversas, Usuario eu) {
        if (conversas.isEmpty()) return List.of();
        List<UUID> ids = conversas.stream().map(Conversa::getId).toList();
        Map<UUID, Long> naoLidas = naoLidas(eu, ids);
        Map<String, Object[]> prefs = conversaRepository.preferencias(eu.getId(), ids).stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> r));
        Map<UUID, Long> totais = membroRepository.contagens(ids).stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> ((Number) r[1]).longValue()));
        Set<UUID> fixadasIds = conversas.stream().map(Conversa::getMensagemFixadaId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Mensagem> fixadas = fixadasIds.isEmpty() ? Map.of()
                : mensagemRepository.porIds(fixadasIds).stream().collect(Collectors.toMap(Mensagem::getId, x -> x));
        return conversas.stream().map(c -> {
            Object[] p = prefs.get(c.getId().toString());
            String titulo = c.getTitulo();
            UUID outro = null;
            if (c.getTipo() == Conversa.Tipo.DIRETA) {
                outro = outroMembro(c, eu);
                if (outro != null) titulo = suporte.usuario(outro).getNome();
            } else if (c.getTipo() == Conversa.Tipo.PRIVADA && !eEquipa(eu)) {
                titulo = "Chuanga Studio";
            }
            Mensagem fix = c.getMensagemFixadaId() != null ? fixadas.get(c.getMensagemFixadaId()) : null;
            boolean membro = c.getTipo() == Conversa.Tipo.PRIVADA || suporte.membro(c, eu).isPresent();
            return new ConversaResponse(c.getId(), c.getTipo().name(), titulo, c.getDescricao(), c.getFotoAnexoId() != null,
                    c.getSistema() != null ? c.getSistema().name() : null, c.getServico() != null ? c.getServico().getId() : null,
                    c.isComunidade(), c.isAberto(), c.getCliente() != null ? c.getCliente().getId() : null, outro,
                    c.getUltimaMensagem(), c.getUltimaMensagemEm(), naoLidas.getOrDefault(c.getId(), 0L), c.isBloqueada(),
                    c.isSoAdminsEditam(), p != null && Boolean.TRUE.equals(p[1]), p != null && Boolean.TRUE.equals(p[2]),
                    p != null && Boolean.TRUE.equals(p[3]), membro, suporte.souAdmin(c, eu), totais.getOrDefault(c.getId(), 0L),
                    fix != null && !fix.isApagada() ? fix.getId() : null,
                    fix != null && !fix.isApagada() ? previa(fix) : null, c.getCriadoEm(), c.getAtualizadoEm());
        }).toList();
    }

    private UUID outroMembro(Conversa c, Usuario eu) {
        return membroRepository.ativos(c.getId()).stream().map(ConversaMembro::getUsuario)
                .filter(id -> !id.equals(eu.getId())).findFirst()
                .orElseGet(() -> membroRepository.findByConversa(c.getId()).stream().map(ConversaMembro::getUsuario)
                        .filter(id -> !id.equals(eu.getId())).findFirst().orElse(null));
    }

    private Map<UUID, LocalDateTime> leituras(Conversa c) {
        Map<UUID, LocalDateTime> r = new HashMap<>();
        conversaRepository.leituras(c.getId()).forEach(l -> r.put(UUID.fromString((String) l[0]), toLdt(l[1])));
        return r;
    }

    private static LocalDateTime toLdt(Object o) {
        if (o instanceof LocalDateTime l) return l;
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        if (o instanceof java.time.Instant i) return LocalDateTime.ofInstant(i, java.time.ZoneId.systemDefault());
        return null;
    }

    private Map<UUID, Long> naoLidas(Usuario eu, List<UUID> conversas) {
        if (conversas.isEmpty()) return Map.of();
        return conversaRepository.naoLidasPorConversa(eu.getId(), conversas).stream()
                .collect(Collectors.toMap(r -> UUID.fromString((String) r[0]), r -> ((Number) r[1]).longValue()));
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
