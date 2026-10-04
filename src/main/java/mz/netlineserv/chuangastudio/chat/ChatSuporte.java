package mz.netlineserv.chuangastudio.chat;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.exception.AcessoNegadoException;
import mz.netlineserv.chuangastudio.common.exception.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;
import mz.netlineserv.chuangastudio.repository.UsuarioRepository;
import mz.netlineserv.chuangastudio.service.UsuarioService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Regras e conversões partilhadas pelo chat: quem vê o quê, quem administra cada grupo,
 * presença ("online"/"a escrever...") e o formato das respostas.
 */
@Component
@RequiredArgsConstructor
public class ChatSuporte {

    /** Como no WhatsApp: editar até 15 minutos, apagar para todos até 2 dias. */
    public static final Duration JANELA_EDICAO = Duration.ofMinutes(15);
    public static final Duration JANELA_APAGAR = Duration.ofDays(2);
    private static final Duration ONLINE = Duration.ofSeconds(25);
    private static final Duration A_ESCREVER = Duration.ofSeconds(6);

    private final ConversaRepository conversaRepository;
    private final ConversaMembroRepository membroRepository;
    private final MensagemRepository mensagemRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    /** Última atividade no chat por utilizador (memória: basta para "online" e "visto por último"). */
    private final Map<UUID, LocalDateTime> vistoEm = new ConcurrentHashMap<>();
    /** conversa -> (utilizador -> instante) de quem está a escrever. */
    private final Map<UUID, Map<UUID, LocalDateTime>> aEscrever = new ConcurrentHashMap<>();
    private final Map<UUID, String> nomes = new ConcurrentHashMap<>();

    // --- Respostas ---

    public record Reacao(String emoji, long total, boolean minha, List<String> nomes) {
    }

    public record MensagemResponse(UUID id, UUID conversaId, UUID remetenteId, String remetenteNome, boolean remetenteEquipa,
                                   boolean minha, String tipo, String conteudo, boolean temAnexo, String anexoNome,
                                   Long anexoTamanho, String anexoTipo, Integer duracaoSegundos, UUID respostaA,
                                   String respostaPreview, String respostaNome, boolean editada, boolean apagada,
                                   boolean encaminhada, boolean favorita, List<Reacao> reacoes, LocalDateTime criadoEm,
                                   LocalDateTime podeEditarAte, LocalDateTime podeApagarAte, boolean podeApagarModeracao) {
    }

    public record ConversaResponse(UUID id, String tipo, String titulo, String descricao, boolean temFoto, String sistema,
                                   UUID servicoId, boolean comunidade, boolean aberto, UUID clienteId, UUID outroUsuarioId,
                                   String ultimaMensagem, LocalDateTime ultimaMensagemEm, long naoLidas, boolean bloqueada,
                                   boolean soAdminsEditam, boolean fixada, boolean arquivada, boolean silenciada,
                                   boolean membro, boolean souAdmin, long totalMembros, UUID mensagemFixadaId,
                                   String mensagemFixadaPreview, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
    }

    // --- Utilizador atual e presença ---

    public Usuario eu() {
        Usuario eu = usuarioService.obterUsuarioOnline();
        vistoEm.put(eu.getId(), LocalDateTime.now());
        nomes.put(eu.getId(), eu.getNome());
        return eu;
    }

    public boolean online(UUID usuario) {
        LocalDateTime v = vistoEm.get(usuario);
        return v != null && v.isAfter(LocalDateTime.now().minus(ONLINE));
    }

    public LocalDateTime vistoEm(UUID usuario) {
        return vistoEm.get(usuario);
    }

    public void aEscrever(UUID conversa, Usuario eu) {
        aEscrever.computeIfAbsent(conversa, k -> new ConcurrentHashMap<>()).put(eu.getId(), LocalDateTime.now());
    }

    public void parouDeEscrever(UUID conversa, UUID usuario) {
        Map<UUID, LocalDateTime> m = aEscrever.get(conversa);
        if (m != null) m.remove(usuario);
    }

    public List<String> quemEscreve(UUID conversa, UUID eu) {
        Map<UUID, LocalDateTime> m = aEscrever.get(conversa);
        if (m == null) return List.of();
        LocalDateTime limite = LocalDateTime.now().minus(A_ESCREVER);
        m.entrySet().removeIf(e -> e.getValue().isBefore(limite));
        return m.keySet().stream().filter(id -> !id.equals(eu)).map(id -> primeiroNome(nomes.getOrDefault(id, ""))).toList();
    }

    // --- Acesso ---

    public static boolean eEquipa(Usuario u) {
        return u.getPapel() == PapelUsuario.ADMIN;
    }

    public Optional<ConversaMembro> membro(Conversa c, Usuario u) {
        return membroRepository.findByConversaAndUsuario(c.getId(), u.getId()).filter(ConversaMembro::isAtivo);
    }

    /** A equipa vê as conversas com o estúdio e todos os grupos (moderação); as diretas só quem lá está. */
    public boolean podeVer(Conversa c, Usuario eu) {
        return switch (c.getTipo()) {
            case PRIVADA -> eEquipa(eu) || (c.getCliente() != null && c.getCliente().getId().equals(eu.getId()));
            case GRUPO -> eEquipa(eu) || membro(c, eu).isPresent();
            case DIRETA -> membro(c, eu).isPresent();
        };
    }

    public void exigirVer(Conversa c, Usuario eu) {
        if (!podeVer(c, eu)) {
            throw new AcessoNegadoException("Sem acesso a esta conversa.");
        }
    }

    /** Administradora do grupo: a equipa sempre, ou uma cliente promovida. */
    public boolean souAdmin(Conversa c, Usuario eu) {
        if (c.getTipo() != Conversa.Tipo.GRUPO) return false;
        return eEquipa(eu) || membro(c, eu).map(ConversaMembro::isAdmin).orElse(false);
    }

    public void exigirAdmin(Conversa c, Usuario eu) {
        if (!souAdmin(c, eu)) {
            throw new AcessoNegadoException("Só as administradoras do grupo podem fazer isto.");
        }
    }

    public void exigirPodeEscrever(Conversa c, Usuario eu) {
        exigirVer(c, eu);
        if (c.getTipo() == Conversa.Tipo.GRUPO && c.isBloqueada() && !souAdmin(c, eu)) {
            throw new RegraNegocioException("Só as administradoras podem enviar mensagens neste grupo.");
        }
        if (c.getTipo() == Conversa.Tipo.DIRETA) {
            // Como no WhatsApp: se a outra pessoa tinha apagado a conversa, ela reaparece com a nova mensagem.
            for (ConversaMembro m : membroRepository.findByConversa(c.getId())) {
                if (m.getUsuario().equals(eu.getId()) || m.isAtivo()) continue;
                if (usuarioRepository.findById(m.getUsuario()).map(u -> u.isDeletado()).orElse(true)) {
                    throw new RegraNegocioException("Esta pessoa já não está disponível.");
                }
                m.setSaiuEm(null);
                membroRepository.save(m);
            }
        }
    }

    /** A equipa que fala num grupo de que não é membro passa a sê-lo (como administradora). */
    public void garantirEquipaNoGrupo(Conversa c, Usuario eu) {
        if (c.getTipo() == Conversa.Tipo.GRUPO && eEquipa(eu)) {
            ConversaMembro m = membroRepository.findByConversaAndUsuario(c.getId(), eu.getId()).orElse(null);
            if (m == null) {
                membroRepository.juntarSeNovo(c.getId(), eu.getId(), true);
            } else if (!m.isAtivo() || !m.isAdmin()) {
                m.setSaiuEm(null);
                m.setAdmin(true);
                membroRepository.save(m);
            }
        }
    }

    public Conversa conversa(UUID id) {
        return conversaRepository.findById(id).filter(c -> !c.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa não encontrada."));
    }

    public Mensagem mensagem(UUID id) {
        return mensagemRepository.findById(id).filter(m -> !m.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mensagem não encontrada."));
    }

    public Usuario usuario(UUID id) {
        return usuarioRepository.findById(id).filter(u -> !u.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pessoa não encontrada."));
    }

    public List<Usuario> equipa() {
        return usuarioRepository.findByPapelAndDeletadoFalseOrderByCriadoEmDesc(PapelUsuario.ADMIN);
    }

    /** Pessoas que participam: membros (grupos/diretas) ou cliente + equipa (privada). */
    public List<Usuario> participantes(Conversa c) {
        if (c.getTipo() == Conversa.Tipo.PRIVADA) {
            List<Usuario> l = new ArrayList<>(equipa());
            if (c.getCliente() != null) l.add(c.getCliente());
            return l;
        }
        return membroRepository.ativos(c.getId()).stream().map(ConversaMembro::getPessoa).toList();
    }

    // --- Mensagens ---

    public Mensagem gravar(Conversa c, Usuario autor, Mensagem.Tipo tipo, String conteudo, UUID anexoId, UUID respostaA,
                           Integer duracao, boolean encaminhada) {
        Mensagem m = new Mensagem();
        m.setConversa(c);
        m.setRemetente(autor);
        m.setTipo(tipo);
        m.setConteudo(conteudo);
        m.setAnexoId(anexoId);
        m.setRespostaA(respostaA);
        m.setDuracaoSegundos(duracao);
        m.setEncaminhada(encaminhada);
        Mensagem salva = mensagemRepository.save(m);
        c.setUltimaMensagem(resumo(autor, tipo, conteudo, c));
        c.setUltimaMensagemEm(LocalDateTime.now());
        conversaRepository.save(c);
        if (tipo != Mensagem.Tipo.SISTEMA) {
            conversaRepository.desarquivar(c.getId());
            conversaRepository.marcarLida(c.getId(), autor.getId(), LocalDateTime.now());
            parouDeEscrever(c.getId(), autor.getId());
        }
        return salva;
    }

    /** Aviso cinzento no meio da conversa ("Ana entrou no grupo"). */
    public void sistema(Conversa c, Usuario autor, String texto) {
        gravar(c, autor, Mensagem.Tipo.SISTEMA, texto, null, null, null, false);
    }

    public List<MensagemResponse> paraResposta(List<Mensagem> mensagens, Usuario eu) {
        if (mensagens.isEmpty()) return List.of();
        Set<UUID> ids = mensagens.stream().map(Mensagem::getId).collect(Collectors.toSet());
        Set<String> ocultas = new HashSet<>(mensagemRepository.ocultasEntre(eu.getId(), ids));
        List<Mensagem> visiveis = mensagens.stream().filter(m -> !ocultas.contains(m.getId().toString())).toList();
        if (visiveis.isEmpty()) return List.of();

        Set<UUID> respondidas = visiveis.stream().map(Mensagem::getRespostaA).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Mensagem> originais = respondidas.isEmpty() ? Map.of()
                : mensagemRepository.porIds(respondidas).stream().collect(Collectors.toMap(Mensagem::getId, Function.identity()));
        Set<UUID> anexos = visiveis.stream().map(Mensagem::getAnexoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, Object[]> meta = anexos.isEmpty() ? Map.of()
                : mensagemRepository.metadadosAnexos(anexos).stream().collect(Collectors.toMap(r -> (String) r[0], r -> r));
        Set<String> favoritas = new HashSet<>(mensagemRepository.favoritasEntre(eu.getId(), ids));
        Map<String, List<Object[]>> reacoes = mensagemRepository.reacoes(ids).stream()
                .collect(Collectors.groupingBy(r -> (String) r[0]));

        boolean moderacao = visiveis.get(0).getConversa().getTipo() == Conversa.Tipo.GRUPO && souAdmin(visiveis.get(0).getConversa(), eu);
        LocalDateTime agora = LocalDateTime.now();
        return visiveis.stream().map(m -> {
            Mensagem orig = m.getRespostaA() != null ? originais.get(m.getRespostaA()) : null;
            boolean minha = m.getRemetente().getId().equals(eu.getId());
            boolean apagada = m.isApagada();
            Object[] a = m.getAnexoId() != null ? meta.get(m.getAnexoId().toString()) : null;
            LocalDateTime editarAte = m.getCriadoEm().plus(JANELA_EDICAO);
            LocalDateTime apagarAte = m.getCriadoEm().plus(JANELA_APAGAR);
            boolean sistema = m.getTipo() == Mensagem.Tipo.SISTEMA;
            return new MensagemResponse(m.getId(), m.getConversa().getId(), m.getRemetente().getId(), m.getRemetente().getNome(),
                    eEquipa(m.getRemetente()), minha, m.getTipo().name(), apagada ? null : m.getConteudo(),
                    a != null && !apagada, a != null && !apagada ? (String) a[2] : null,
                    a != null && !apagada && a[3] != null ? ((Number) a[3]).longValue() : null,
                    a != null && !apagada ? (String) a[1] : null, m.getDuracaoSegundos(),
                    m.getRespostaA(), orig == null ? null : previa(orig), orig == null ? null : orig.getRemetente().getNome(),
                    m.getEditadaEm() != null, apagada, m.isEncaminhada(), favoritas.contains(m.getId().toString()),
                    agruparReacoes(reacoes.getOrDefault(m.getId().toString(), List.of()), eu),
                    m.getCriadoEm(),
                    minha && !apagada && !sistema && m.getTipo() == Mensagem.Tipo.TEXTO && agora.isBefore(editarAte) ? editarAte : null,
                    minha && !apagada && !sistema && agora.isBefore(apagarAte) ? apagarAte : null,
                    moderacao && !apagada && !sistema && !minha);
        }).toList();
    }

    private static List<Reacao> agruparReacoes(List<Object[]> linhas, Usuario eu) {
        Map<String, List<Object[]>> porEmoji = new LinkedHashMap<>();
        linhas.forEach(r -> porEmoji.computeIfAbsent((String) r[1], k -> new ArrayList<>()).add(r));
        String meu = eu.getId().toString();
        return porEmoji.entrySet().stream().map(e -> new Reacao(e.getKey(), e.getValue().size(),
                e.getValue().stream().anyMatch(r -> meu.equals(r[2])),
                e.getValue().stream().map(r -> (String) r[3]).toList())).toList();
    }

    public static String previa(Mensagem m) {
        if (m.isApagada()) return "Mensagem apagada";
        String t = m.getConteudo() == null ? "" : m.getConteudo();
        String p = switch (m.getTipo()) {
            case IMAGEM -> "📷 Foto" + (t.isBlank() ? "" : " · " + t);
            case AUDIO -> "🎤 Mensagem de voz";
            case DOCUMENTO -> "📄 " + (t.isBlank() ? "Documento" : t);
            default -> t;
        };
        return p.length() > 120 ? p.substring(0, 119) + "…" : p;
    }

    private String resumo(Usuario autor, Mensagem.Tipo tipo, String conteudo, Conversa c) {
        if (tipo == Mensagem.Tipo.SISTEMA) {
            return conteudo;
        }
        Mensagem tmp = new Mensagem();
        tmp.setTipo(tipo);
        tmp.setConteudo(conteudo);
        String texto = previa(tmp);
        String prefixo = c.getTipo() == Conversa.Tipo.GRUPO || (c.getTipo() == Conversa.Tipo.PRIVADA && eEquipa(autor))
                ? primeiroNome(autor.getNome()) + ": " : "";
        String r = prefixo + texto;
        return r.length() > 300 ? r.substring(0, 299) + "…" : r;
    }

    public static String primeiroNome(String nome) {
        return nome == null || nome.isBlank() ? "" : nome.trim().split("\\s+")[0];
    }
}
