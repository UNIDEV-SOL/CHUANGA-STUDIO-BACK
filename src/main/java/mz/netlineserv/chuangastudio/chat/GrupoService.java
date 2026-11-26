package mz.netlineserv.chuangastudio.chat;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.exception.AcessoNegadoException;
import mz.netlineserv.chuangastudio.common.exception.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import mz.netlineserv.chuangastudio.domain.entity.Marcacao;
import mz.netlineserv.chuangastudio.domain.entity.MarcacaoServico;
import mz.netlineserv.chuangastudio.domain.entity.Servico;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;
import mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao;
import mz.netlineserv.chuangastudio.notificacao.DadosEstudio;
import mz.netlineserv.chuangastudio.repository.MarcacaoRepository;
import mz.netlineserv.chuangastudio.repository.ServicoRepository;
import mz.netlineserv.chuangastudio.repository.UsuarioRepository;
import mz.netlineserv.chuangastudio.repository.spec.BaseSpecification;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static mz.netlineserv.chuangastudio.chat.ChatSuporte.eEquipa;
import static mz.netlineserv.chuangastudio.chat.ChatSuporte.primeiroNome;

/**
 * Grupos e comunidade, como no WhatsApp:
 *  - qualquer pessoa cria um grupo e fica administradora; a equipa é administradora de todos;
 *  - administradoras adicionam/removem pessoas, promovem outras e escolhem quem envia e quem edita;
 *  - a comunidade junta o grupo de avisos, a conversa geral e os grupos de cada serviço;
 *  - quem marca um serviço entra sozinha no grupo desse serviço (se não tiver saído antes).
 */
@Service
@RequiredArgsConstructor
public class GrupoService {

    private final ChatSuporte suporte;
    private final ConversaRepository conversaRepository;
    private final ConversaMembroRepository membroRepository;
    private final MensagemRepository mensagemRepository;
    private final ChatAnexoRepository anexoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MarcacaoRepository marcacaoRepository;
    private final ServicoRepository servicoRepository;
    private final DadosEstudio dadosEstudio;

    public record CriarGrupoRequest(String titulo, String descricao, List<UUID> membros, Boolean comunidade, Boolean aberto,
                                    Boolean bloqueada, UUID servicoId) {
    }

    public record AtualizarGrupoRequest(String titulo, String descricao, Boolean bloqueada, Boolean soAdminsEditam,
                                        Boolean aberto, Boolean comunidade) {
    }

    public record MembroResponse(UUID usuarioId, String nome, boolean admin, boolean equipa, boolean eu, boolean online,
                                 LocalDateTime vistoEm, String telefone, LocalDateTime entrouEm) {
    }

    public record ContactoDetalhe(UUID usuarioId, String nome, String telefone, String email, boolean equipa, boolean online,
                                  LocalDateTime vistoEm, LocalDateTime clienteDesde, Long visitas) {
    }

    public record GrupoResumo(UUID id, String titulo) {
    }

    public record Detalhes(List<MembroResponse> membros, ContactoDetalhe contacto, List<GrupoResumo> gruposEmComum,
                           long totalFotos, long totalDocumentos, boolean podeEditarInfo, boolean podeSair, boolean podeApagar) {
    }

    public record ComunidadeItem(UUID id, String titulo, String descricao, boolean temFoto, String sistema, UUID servicoId,
                                 boolean aberto, boolean membro, boolean podeEntrar, long totalMembros, LocalDateTime ultimaMensagemEm) {
    }

    public record Contacto(UUID usuarioId, String nome, boolean equipa) {
    }

    // --- Comunidade e entrada automática ---

    /** Toda a gente pertence à comunidade e aos avisos (a não ser que tenha saído). */
    @Transactional
    public void garantirComunidade(Usuario eu) {
        for (Conversa.Sistema s : Conversa.Sistema.values()) {
            Conversa c = grupoDeSistema(s);
            membroRepository.juntarSeNovo(c.getId(), eu.getId(), eEquipa(eu));
        }
    }

    private Conversa grupoDeSistema(Conversa.Sistema s) {
        return conversaRepository.findFirstBySistemaAndDeletadoFalse(s).orElseGet(() -> {
            Conversa c = new Conversa();
            c.setTipo(Conversa.Tipo.GRUPO);
            c.setSistema(s);
            c.setComunidade(true);
            if (s == Conversa.Sistema.AVISOS) {
                c.setTitulo("Avisos Chuanga Studio");
                c.setDescricao("Novidades, promoções e horários do estúdio. Só a equipa escreve aqui.");
                c.setBloqueada(true);
            } else {
                c.setTitulo("Comunidade Chuanga");
                c.setDescricao("Espaço de conversa entre as clientes do Chuanga Studio. Seja simpática: a equipa modera.");
            }
            return conversaRepository.save(c);
        });
    }

    /** Depois de uma marcação: a cliente entra nos grupos dos serviços marcados (cria o grupo se for o primeiro). */
    @Transactional
    public void juntarAosGruposDaMarcacao(UUID marcacaoId) {
        Marcacao m = marcacaoRepository.buscarPorIdComDetalhes(marcacaoId).orElse(null);
        if (m == null || m.getStatus() == StatusMarcacao.CANCELADA || m.getCliente() == null) {
            return;
        }
        Usuario cliente = m.getCliente();
        garantirComunidade(cliente);
        for (MarcacaoServico ms : m.getServicos()) {
            Servico s = ms.getServico();
            if (s == null || s.isDeletado()) continue;
            Conversa g = grupoDoServico(s);
            if (membroRepository.juntarSeNovo(g.getId(), cliente.getId(), false) == 1) {
                suporte.sistema(g, cliente, primeiroNome(cliente.getNome()) + " juntou-se ao grupo");
            }
        }
    }

    private Conversa grupoDoServico(Servico s) {
        return conversaRepository.findFirstByServicoIdAndDeletadoFalse(s.getId()).orElseGet(() -> {
            Conversa c = new Conversa();
            c.setTipo(Conversa.Tipo.GRUPO);
            c.setTitulo(s.getNome());
            c.setServico(s);
            c.setComunidade(true);
            c.setDescricao("Para quem já fez " + s.getNome() + " no Chuanga Studio: dicas de cuidados, fotos e dúvidas.");
            Conversa salvo = conversaRepository.save(c);
            suporte.equipa().forEach(u -> membroRepository.juntarSeNovo(salvo.getId(), u.getId(), true));
            return salvo;
        });
    }

    @Transactional
    public List<ComunidadeItem> comunidade() {
        Usuario eu = suporte.eu();
        garantirComunidade(eu);
        List<Conversa> grupos = conversaRepository.findAll(BaseSpecification.of(Conversa.class)
                .equals("tipo", Conversa.Tipo.GRUPO).equals("comunidade", true).build());
        Map<UUID, Long> totais = new HashMap<>();
        if (!grupos.isEmpty()) {
            membroRepository.contagens(grupos.stream().map(Conversa::getId).toList())
                    .forEach(r -> totais.put((UUID) r[0], ((Number) r[1]).longValue()));
        }
        Set<UUID> minhas = new HashSet<>(membroRepository.conversasDe(eu.getId()));
        return grupos.stream()
                .sorted(Comparator.comparing((Conversa c) -> c.getSistema() == Conversa.Sistema.AVISOS ? 0 : c.getSistema() != null ? 1 : 2)
                        .thenComparing(Conversa::getTitulo, String.CASE_INSENSITIVE_ORDER))
                .map(c -> {
                    boolean membro = minhas.contains(c.getId());
                    boolean podeEntrar = !membro && (c.getSistema() != null || c.isAberto() || eEquipa(eu)
                            || (c.getServico() != null && jaMarcou(eu, c.getServico().getId())));
                    return new ComunidadeItem(c.getId(), c.getTitulo(), c.getDescricao(), c.getFotoAnexoId() != null,
                            c.getSistema() != null ? c.getSistema().name() : null, c.getServico() != null ? c.getServico().getId() : null,
                            c.isAberto(), membro, podeEntrar, totais.getOrDefault(c.getId(), 0L), c.getUltimaMensagemEm());
                }).toList();
    }

    private boolean jaMarcou(Usuario u, UUID servicoId) {
        return marcacaoRepository.count(BaseSpecification.of(Marcacao.class)
                .equals("cliente.id", u.getId())
                .and((root, q, cb) -> {
                    var sub = q.subquery(UUID.class);
                    var ms = sub.from(MarcacaoServico.class);
                    sub.select(ms.get("marcacao").get("id")).where(cb.equal(ms.get("servico").get("id"), servicoId));
                    return cb.and(root.get("id").in(sub), cb.notEqual(root.get("status"), StatusMarcacao.CANCELADA));
                }).build()) > 0;
    }

    // --- Conversas diretas ---

    /** Abre (ou cria) a conversa com uma pessoa. Cliente ↔ equipa usa a conversa com o estúdio. */
    @Transactional
    public UUID abrirCom(UUID usuarioId) {
        Usuario eu = suporte.eu();
        Usuario outra = suporte.usuario(usuarioId);
        if (outra.getId().equals(eu.getId())) {
            throw new RegraNegocioException("Não pode abrir uma conversa consigo.");
        }
        if (!eEquipa(eu) && eEquipa(outra)) return conversaPrivada(eu).getId();
        if (eEquipa(eu) && outra.getPapel() == PapelUsuario.CLIENTE) return conversaPrivada(outra).getId();
        if (!eEquipa(eu) && membroRepository.gruposEmComum(eu.getId(), outra.getId()).isEmpty()) {
            throw new AcessoNegadoException("Só pode escrever a pessoas dos seus grupos.");
        }
        Optional<String> existente = membroRepository.direta(eu.getId(), outra.getId());
        if (existente.isPresent()) {
            UUID id = UUID.fromString(existente.get());
            // Se alguém tinha "saído" (apagado a conversa), volta a estar.
            reativar(id, eu.getId());
            reativar(id, outra.getId());
            return id;
        }
        Conversa c = new Conversa();
        c.setTipo(Conversa.Tipo.DIRETA);
        c.setTitulo(eu.getNome() + " · " + outra.getNome());
        Conversa salva = conversaRepository.save(c);
        membroRepository.juntarSeNovo(salva.getId(), eu.getId(), false);
        membroRepository.juntarSeNovo(salva.getId(), outra.getId(), false);
        return salva.getId();
    }

    private void reativar(UUID conversa, UUID usuario) {
        membroRepository.findByConversaAndUsuario(conversa, usuario).filter(m -> !m.isAtivo()).ifPresent(m -> {
            m.setSaiuEm(null);
            membroRepository.save(m);
        });
    }

    /** A equipa abre (ou cria) a conversa com uma cliente, ex. a partir da ficha. */
    @Transactional
    public UUID conversaComCliente(UUID clienteId) {
        if (!eEquipa(suporte.eu())) throw new AcessoNegadoException("Apenas a equipa.");
        Usuario cliente = suporte.usuario(clienteId);
        if (cliente.getPapel() != PapelUsuario.CLIENTE) throw new RecursoNaoEncontradoException("Cliente não encontrada.");
        return conversaPrivada(cliente).getId();
    }

    private Conversa conversaPrivada(Usuario cliente) {
        return conversaRepository.findFirstByTipoAndClienteIdAndDeletadoFalse(Conversa.Tipo.PRIVADA, cliente.getId())
                .orElseGet(() -> {
                    Conversa c = new Conversa();
                    c.setTipo(Conversa.Tipo.PRIVADA);
                    c.setTitulo(cliente.getNome());
                    c.setCliente(cliente);
                    return conversaRepository.save(c);
                });
    }

    /** Pessoas para nova conversa / adicionar ao grupo. A equipa vê toda a gente; as clientes, quem está nos seus grupos. */
    @Transactional(readOnly = true)
    public List<Contacto> contactos(String busca) {
        Usuario eu = suporte.eu();
        String padrao = busca == null ? "" : busca.trim();
        if (eEquipa(eu)) {
            return usuarioRepository.findAll(BaseSpecification.of(Usuario.class)
                            .likeAny(padrao, "nome", "telefone", "email")
                            .and((root, q, cb) -> cb.notEqual(root.get("id"), eu.getId())).build(),
                    PageRequest.of(0, 60, Sort.by("nome"))).stream()
                    .map(u -> new Contacto(u.getId(), u.getNome(), eEquipa(u))).toList();
        }
        List<Contacto> r = new ArrayList<>();
        r.add(new Contacto(suporte.equipa().stream().findFirst().map(Usuario::getId).orElse(null), "Chuanga Studio (equipa)", true));
        Set<UUID> vistos = new HashSet<>();
        for (UUID conversa : membroRepository.conversasDe(eu.getId())) {
            for (ConversaMembro m : membroRepository.ativos(conversa)) {
                Usuario u = m.getPessoa();
                if (u.getId().equals(eu.getId()) || eEquipa(u) || !vistos.add(u.getId())) continue;
                if (padrao.isEmpty() || u.getNome().toLowerCase().contains(padrao.toLowerCase())) {
                    r.add(new Contacto(u.getId(), u.getNome(), false));
                }
            }
        }
        r.subList(1, r.size()).sort(Comparator.comparing(Contacto::nome, String.CASE_INSENSITIVE_ORDER));
        return r.size() > 61 ? r.subList(0, 61) : r;
    }

    // --- Grupos ---

    @Transactional
    public UUID criar(CriarGrupoRequest r) {
        Usuario eu = suporte.eu();
        String titulo = obrigatorio(r.titulo(), "Dê um nome ao grupo.", 120);
        Conversa c = new Conversa();
        c.setTipo(Conversa.Tipo.GRUPO);
        c.setTitulo(titulo);
        c.setDescricao(opcional(r.descricao(), 500));
        c.setBloqueada(Boolean.TRUE.equals(r.bloqueada()));
        if (eEquipa(eu)) {
            c.setComunidade(Boolean.TRUE.equals(r.comunidade()));
            c.setAberto(Boolean.TRUE.equals(r.aberto()));
            if (r.servicoId() != null) {
                Servico s = servicoRepository.findById(r.servicoId()).filter(x -> !x.isDeletado())
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado."));
                if (conversaRepository.findFirstByServicoIdAndDeletadoFalse(s.getId()).isPresent()) {
                    throw new RegraNegocioException("Já existe um grupo para " + s.getNome() + ".");
                }
                c.setServico(s);
                c.setComunidade(true);
            }
        }
        Conversa salva = conversaRepository.save(c);
        membroRepository.juntarSeNovo(salva.getId(), eu.getId(), true);
        suporte.equipa().forEach(u -> membroRepository.juntarSeNovo(salva.getId(), u.getId(), true));
        suporte.sistema(salva, eu, primeiroNome(eu.getNome()) + " criou o grupo \"" + titulo + "\"");
        if (r.membros() != null && !r.membros().isEmpty()) {
            adicionarSemVerificar(salva, eu, r.membros());
        }
        if (salva.getServico() != null) {
            // Grupo de serviço criado à mão: entram já as clientes que o marcaram.
            marcacaoRepository.findAll(BaseSpecification.of(Marcacao.class).build()).stream()
                    .filter(m -> m.getStatus() != StatusMarcacao.CANCELADA
                            && m.getServicos().stream().anyMatch(ms -> ms.getServico() != null && ms.getServico().getId().equals(salva.getServico().getId())))
                    .map(Marcacao::getCliente).distinct()
                    .forEach(u -> membroRepository.juntarSeNovo(salva.getId(), u.getId(), false));
        }
        return salva.getId();
    }

    @Transactional
    public void atualizar(UUID id, AtualizarGrupoRequest r) {
        Usuario eu = suporte.eu();
        Conversa c = grupo(id);
        boolean admin = suporte.souAdmin(c, eu);
        boolean mudaInfo = r.titulo() != null || r.descricao() != null;
        if (mudaInfo) {
            exigirPodeEditarInfo(c, eu);
            if (r.titulo() != null) {
                String novo = obrigatorio(r.titulo(), "O nome não pode ficar vazio.", 120);
                if (!novo.equals(c.getTitulo())) {
                    c.setTitulo(novo);
                    suporte.sistema(c, eu, primeiroNome(eu.getNome()) + " mudou o nome do grupo para \"" + novo + "\"");
                }
            }
            if (r.descricao() != null && !Objects.equals(opcional(r.descricao(), 500), c.getDescricao())) {
                c.setDescricao(opcional(r.descricao(), 500));
                suporte.sistema(c, eu, primeiroNome(eu.getNome()) + " mudou a descrição do grupo");
            }
        }
        if (r.bloqueada() != null || r.soAdminsEditam() != null) {
            if (!admin) throw new AcessoNegadoException("Só as administradoras mudam as definições do grupo.");
            if (r.bloqueada() != null && r.bloqueada() != c.isBloqueada()) {
                c.setBloqueada(r.bloqueada());
                suporte.sistema(c, eu, primeiroNome(eu.getNome()) + (r.bloqueada()
                        ? " definiu que só as administradoras enviam mensagens" : " permitiu que todas enviem mensagens"));
            }
            if (r.soAdminsEditam() != null) c.setSoAdminsEditam(r.soAdminsEditam());
        }
        if (r.aberto() != null || r.comunidade() != null) {
            if (!eEquipa(eu)) throw new AcessoNegadoException("Só a equipa decide o que aparece na comunidade.");
            if (r.aberto() != null) c.setAberto(r.aberto());
            if (r.comunidade() != null && c.getSistema() == null) c.setComunidade(r.comunidade());
        }
        conversaRepository.save(c);
    }

    @Transactional
    public void foto(UUID id, MultipartFile imagem) throws IOException {
        Usuario eu = suporte.eu();
        Conversa c = grupo(id);
        exigirPodeEditarInfo(c, eu);
        if (imagem.isEmpty()) {
            throw new RegraNegocioException("A imagem enviada está vazia (0 bytes).");
        }
        if (imagem.getSize() > 5L * 1024 * 1024) {
            throw new RegraNegocioException(String.format("A foto do grupo tem %.1f MB; o máximo é 5 MB.", imagem.getSize() / 1024.0 / 1024.0));
        }
        String tipo = TiposAnexo.exigirImagem(imagem.getContentType());
        ChatAnexo anexo = new ChatAnexo();
        anexo.setId(UUID.randomUUID());
        anexo.setContentType(tipo);
        anexo.setTamanho(imagem.getSize());
        anexo.setDados(imagem.getBytes());
        anexoRepository.save(anexo);
        c.setFotoAnexoId(anexo.getId());
        conversaRepository.save(c);
        suporte.sistema(c, eu, primeiroNome(eu.getNome()) + " mudou a foto do grupo");
    }

    @Transactional(readOnly = true)
    public ChatAnexo fotoAnexo(UUID id) {
        Usuario eu = suporte.eu();
        Conversa c = suporte.conversa(id);
        // A foto dos grupos da comunidade vê-se mesmo antes de entrar.
        if (!(c.getTipo() == Conversa.Tipo.GRUPO && c.isComunidade())) suporte.exigirVer(c, eu);
        if (c.getFotoAnexoId() == null) throw new RecursoNaoEncontradoException("Sem foto.");
        return anexoRepository.findById(c.getFotoAnexoId()).orElseThrow(() -> new RecursoNaoEncontradoException("Sem foto."));
    }

    @Transactional
    public void adicionar(UUID id, List<UUID> usuarios) {
        Usuario eu = suporte.eu();
        Conversa c = grupo(id);
        suporte.exigirAdmin(c, eu);
        if (usuarios == null || usuarios.isEmpty()) throw new RegraNegocioException("Escolha quem adicionar.");
        adicionarSemVerificar(c, eu, usuarios);
    }

    private void adicionarSemVerificar(Conversa c, Usuario eu, List<UUID> usuarios) {
        List<String> nomes = new ArrayList<>();
        for (UUID uid : new LinkedHashSet<>(usuarios)) {
            Usuario u = suporte.usuario(uid);
            ConversaMembro m = membroRepository.findByConversaAndUsuario(c.getId(), uid).orElse(null);
            if (m == null) {
                membroRepository.juntarSeNovo(c.getId(), uid, eEquipa(u));
            } else if (!m.isAtivo()) {
                m.setSaiuEm(null);
                m.setEntrouEm(LocalDateTime.now());
                membroRepository.save(m);
            } else {
                continue;
            }
            nomes.add(primeiroNome(u.getNome()));
        }
        if (!nomes.isEmpty()) {
            suporte.sistema(c, eu, primeiroNome(eu.getNome()) + " adicionou " + juntarNomes(nomes));
        }
    }

    @Transactional
    public void remover(UUID id, UUID usuarioId) {
        Usuario eu = suporte.eu();
        Conversa c = grupo(id);
        suporte.exigirAdmin(c, eu);
        Usuario alvo = suporte.usuario(usuarioId);
        if (eEquipa(alvo) && !eEquipa(eu)) throw new AcessoNegadoException("Não pode remover a equipa do estúdio.");
        ConversaMembro m = membroAtivo(c, usuarioId);
        m.setSaiuEm(LocalDateTime.now());
        m.setAdmin(false);
        membroRepository.save(m);
        suporte.sistema(c, eu, primeiroNome(eu.getNome()) + " removeu " + primeiroNome(alvo.getNome()));
    }

    @Transactional
    public void definirAdmin(UUID id, UUID usuarioId, boolean admin) {
        Usuario eu = suporte.eu();
        Conversa c = grupo(id);
        suporte.exigirAdmin(c, eu);
        Usuario alvo = suporte.usuario(usuarioId);
        if (eEquipa(alvo) && !admin) throw new RegraNegocioException("A equipa é sempre administradora.");
        ConversaMembro m = membroAtivo(c, usuarioId);
        if (m.isAdmin() == admin) return;
        m.setAdmin(admin);
        membroRepository.save(m);
        suporte.sistema(c, eu, admin ? primeiroNome(alvo.getNome()) + " é agora administradora"
                : primeiroNome(alvo.getNome()) + " deixou de ser administradora");
    }

    @Transactional
    public void sair(UUID id) {
        Usuario eu = suporte.eu();
        Conversa c = suporte.conversa(id);
        if (c.getTipo() == Conversa.Tipo.PRIVADA) throw new RegraNegocioException("Não pode sair da conversa com o estúdio.");
        ConversaMembro m = membroAtivo(c, eu.getId());
        m.setSaiuEm(LocalDateTime.now());
        m.setAdmin(false);
        membroRepository.save(m);
        if (c.getTipo() == Conversa.Tipo.GRUPO) {
            suporte.sistema(c, eu, primeiroNome(eu.getNome()) + " saiu");
        }
    }

    @Transactional
    public void entrar(UUID id) {
        Usuario eu = suporte.eu();
        Conversa c = grupo(id);
        boolean permitido = c.isComunidade() && (c.getSistema() != null || c.isAberto() || eEquipa(eu)
                || (c.getServico() != null && jaMarcou(eu, c.getServico().getId())));
        if (!permitido) throw new AcessoNegadoException("Este grupo é só por convite das administradoras.");
        ConversaMembro m = membroRepository.findByConversaAndUsuario(c.getId(), eu.getId()).orElse(null);
        if (m != null && m.isAtivo()) return;
        if (m == null) {
            membroRepository.juntarSeNovo(c.getId(), eu.getId(), eEquipa(eu));
        } else {
            m.setSaiuEm(null);
            m.setEntrouEm(LocalDateTime.now());
            m.setAdmin(eEquipa(eu));
            membroRepository.save(m);
        }
        suporte.sistema(c, eu, primeiroNome(eu.getNome()) + " entrou no grupo");
    }

    @Transactional
    public void apagar(UUID id) {
        Usuario eu = suporte.eu();
        Conversa c = grupo(id);
        if (!eEquipa(eu)) throw new AcessoNegadoException("Só a equipa pode apagar grupos.");
        if (c.getSistema() != null) throw new RegraNegocioException("Os grupos da comunidade principal não se apagam.");
        c.setDeletado(true);
        c.setDeletadoEm(LocalDateTime.now());
        conversaRepository.save(c);
    }

    // --- Detalhes ---

    @Transactional(readOnly = true)
    public Detalhes detalhes(UUID id) {
        Usuario eu = suporte.eu();
        Conversa c = suporte.conversa(id);
        suporte.exigirVer(c, eu);
        long fotos = mensagemRepository.porTipo(id, List.of(Mensagem.Tipo.IMAGEM), PageRequest.of(0, 1)).getTotalElements();
        long docs = mensagemRepository.porTipo(id, List.of(Mensagem.Tipo.DOCUMENTO, Mensagem.Tipo.AUDIO), PageRequest.of(0, 1)).getTotalElements();

        if (c.getTipo() == Conversa.Tipo.GRUPO) {
            List<MembroResponse> membros = membroRepository.ativos(id).stream().map(m -> membro(m.getPessoa(), m.isAdmin() || eEquipa(m.getPessoa()), eu, m.getEntrouEm())).toList();
            boolean souMembro = suporte.membro(c, eu).isPresent();
            return new Detalhes(membros, null, List.of(), fotos, docs, podeEditarInfo(c, eu), souMembro,
                    eEquipa(eu) && c.getSistema() == null);
        }

        Usuario outra;
        if (c.getTipo() == Conversa.Tipo.PRIVADA) {
            outra = eEquipa(eu) ? c.getCliente() : null;
        } else {
            outra = membroRepository.findByConversa(id).stream().map(ConversaMembro::getUsuario)
                    .filter(u -> !u.equals(eu.getId())).findFirst().map(suporte::usuario).orElse(null);
        }
        ContactoDetalhe contacto;
        List<GrupoResumo> comuns = List.of();
        if (outra == null) {
            // A cliente a ver a conversa com o estúdio.
            Map<String, Object> estudio = dadosEstudio.paraTemplate();
            boolean equipaOnline = suporte.equipa().stream().anyMatch(u -> suporte.online(u.getId()));
            contacto = new ContactoDetalhe(null, "Chuanga Studio", (String) estudio.get("telefone"), null, true, equipaOnline,
                    null, null, null);
        } else {
            boolean euEquipa = eEquipa(eu);
            Long visitas = euEquipa && outra.getPapel() == PapelUsuario.CLIENTE
                    ? marcacaoRepository.count(BaseSpecification.of(Marcacao.class).equals("cliente.id", outra.getId())
                    .equals("status", StatusMarcacao.CONCLUIDA).build()) : null;
            contacto = new ContactoDetalhe(outra.getId(), outra.getNome(), euEquipa ? outra.getTelefone() : null,
                    euEquipa ? outra.getEmail() : null, eEquipa(outra), suporte.online(outra.getId()), suporte.vistoEm(outra.getId()),
                    euEquipa ? outra.getCriadoEm() : null, visitas);
            Usuario o = outra;
            comuns = euEquipa
                    ? membroRepository.conversasDe(o.getId()).stream().map(suporte::conversa)
                    .filter(g -> g.getTipo() == Conversa.Tipo.GRUPO).map(g -> new GrupoResumo(g.getId(), g.getTitulo()))
                    .sorted(Comparator.comparing(GrupoResumo::titulo)).toList()
                    : membroRepository.gruposEmComum(eu.getId(), o.getId()).stream()
                    .map(r -> new GrupoResumo(UUID.fromString((String) r[0]), (String) r[1])).toList();
        }
        return new Detalhes(List.of(), contacto, comuns, fotos, docs, false, c.getTipo() == Conversa.Tipo.DIRETA, false);
    }

    private MembroResponse membro(Usuario u, boolean admin, Usuario eu, LocalDateTime entrouEm) {
        return new MembroResponse(u.getId(), u.getNome(), admin, eEquipa(u), u.getId().equals(eu.getId()), suporte.online(u.getId()),
                suporte.vistoEm(u.getId()), eEquipa(eu) ? u.getTelefone() : null, entrouEm);
    }

    // --- Auxiliares ---

    private boolean podeEditarInfo(Conversa c, Usuario eu) {
        if (c.getTipo() != Conversa.Tipo.GRUPO) return false;
        return suporte.souAdmin(c, eu) || (!c.isSoAdminsEditam() && suporte.membro(c, eu).isPresent());
    }

    private void exigirPodeEditarInfo(Conversa c, Usuario eu) {
        if (!podeEditarInfo(c, eu)) throw new AcessoNegadoException("Só as administradoras podem mudar os dados do grupo.");
    }

    private Conversa grupo(UUID id) {
        Conversa c = suporte.conversa(id);
        if (c.getTipo() != Conversa.Tipo.GRUPO) throw new RegraNegocioException("Esta conversa não é um grupo.");
        return c;
    }

    private ConversaMembro membroAtivo(Conversa c, UUID usuario) {
        return membroRepository.findByConversaAndUsuario(c.getId(), usuario).filter(ConversaMembro::isAtivo)
                .orElseThrow(() -> new RegraNegocioException("Esta pessoa não está no grupo."));
    }

    private static String obrigatorio(String v, String erro, int max) {
        if (v == null || v.isBlank()) throw new RegraNegocioException(erro);
        String t = v.trim();
        if (t.length() > max) throw new RegraNegocioException("Pode ter até " + max + " caracteres.");
        return t;
    }

    private static String opcional(String v, int max) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String juntarNomes(List<String> nomes) {
        if (nomes.size() == 1) return nomes.get(0);
        return String.join(", ", nomes.subList(0, nomes.size() - 1)) + " e " + nomes.get(nomes.size() - 1);
    }
}
