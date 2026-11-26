package mz.netlineserv.chuangastudio.modules.bookings.service;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.exceptions.AcessoNegadoException;
import mz.netlineserv.chuangastudio.shared.exceptions.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.shared.exceptions.RegraNegocioException;
import mz.netlineserv.chuangastudio.modules.users.domain.PapelUsuario;
import mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao;
import mz.netlineserv.chuangastudio.modules.bookings.request.CriarMarcacaoRequest;
import mz.netlineserv.chuangastudio.modules.bookings.vm.MarcacaoResponse;
import mz.netlineserv.chuangastudio.modules.notifications.event.NotificacaoMarcacaoEvent;
import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import mz.netlineserv.chuangastudio.modules.bookings.request.FiltroMarcacoes;
import mz.netlineserv.chuangastudio.modules.invoices.repository.FaturaRepository;
import mz.netlineserv.chuangastudio.modules.bookings.repository.MarcacaoRepository;
import mz.netlineserv.chuangastudio.shared.persistence.BaseSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import mz.netlineserv.chuangastudio.modules.professionals.repository.ProfissionalRepository;
import mz.netlineserv.chuangastudio.modules.catalog.repository.ServicoRepository;
import mz.netlineserv.chuangastudio.modules.users.repository.UsuarioRepository;
import mz.netlineserv.chuangastudio.modules.schedule.service.AgendaService;
import mz.netlineserv.chuangastudio.modules.locations.service.MoradaResolver;
import mz.netlineserv.chuangastudio.modules.users.service.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import mz.netlineserv.chuangastudio.modules.bookings.domain.Marcacao;
import mz.netlineserv.chuangastudio.modules.bookings.domain.MarcacaoServico;
import mz.netlineserv.chuangastudio.modules.catalog.domain.Servico;
import mz.netlineserv.chuangastudio.modules.invoices.domain.Fatura;
import mz.netlineserv.chuangastudio.modules.professionals.domain.Profissional;
import mz.netlineserv.chuangastudio.modules.users.domain.Usuario;

@Service
@RequiredArgsConstructor
public class MarcacaoService {

    private static final BigDecimal VALOR_ADIANTAMENTO_PADRAO = new BigDecimal("150.00");
    private static final int TAMANHO_MINIMO_REFERENCIA = 4;
    private static final ZoneId FUSO_ESTUDIO = ZoneId.of("Africa/Maputo");

    private final MarcacaoRepository marcacaoRepository;
    private final ServicoRepository servicoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioService usuarioService;
    private final ApplicationEventPublisher eventPublisher;
    private final AgendaService agendaService;
    private final ProfissionalRepository profissionalRepository;
    private final FaturaRepository faturaRepository;
    private final MoradaResolver moradaResolver;

    @Value("${app.marcacoes.antecedencia-remarcar-horas:24}")
    private int antecedenciaRemarcarHoras;

    @Transactional
    public MarcacaoResponse criar(CriarMarcacaoRequest request, boolean autenticado) {

        if (request.getReferenciaPagamento() == null
                || request.getReferenciaPagamento().trim().length() < TAMANHO_MINIMO_REFERENCIA) {
            throw new RegraNegocioException(
                    "Referência de pagamento inválida. Informe pelo menos " + TAMANHO_MINIMO_REFERENCIA + " caracteres.");
        }

        LocalDateTime inicio = LocalDateTime.of(request.getData(), request.getHora());
        if (inicio.isBefore(LocalDateTime.now(FUSO_ESTUDIO))) {
            throw new RegraNegocioException("Não é possível marcar para uma data ou hora que já passou.");
        }

        Usuario cliente = autenticado ? usuarioService.obterUsuarioOnline() : obterOuCriarCliente(request);
        if (cliente.getPapel() == PapelUsuario.ADMIN) {
            throw new RegraNegocioException("Contas de administrador não podem fazer reservas. Use uma conta de cliente.");
        }

        List<UUID> idsPedidos = request.getServicosIds().stream().distinct().toList();
        List<Servico> servicos = servicoRepository.findAllById(idsPedidos).stream()
                .filter(s -> !s.isDeletado() && s.isAtivo())
                .toList();
        if (servicos.isEmpty() || servicos.size() != idsPedidos.size()) {
            throw new RecursoNaoEncontradoException("Um ou mais serviços selecionados não foram encontrados.");
        }

        int duracaoMinutos = servicos.stream().mapToInt(Servico::getDuracaoMinutos).sum();
        // Horário de funcionamento, dias fechados, pausa e sobreposição com outras marcações.
        Profissional escolhida = request.getProfissionalId() != null ? profissionalAtiva(request.getProfissionalId()) : null;
        agendaService.validarMarcacao(request.getData(), request.getHora(), duracaoMinutos, null,
                escolhida != null ? escolhida.getId() : null);

        BigDecimal valorTotal = servicos.stream()
                .map(Servico::getPreco)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Marcacao marcacao = new Marcacao();
        marcacao.setCliente(cliente);
        marcacao.setDataMarcacao(request.getData());
        marcacao.setHoraMarcacao(request.getHora());
        marcacao.setStatus(StatusMarcacao.AGUARDA_CONFIRMACAO);
        marcacao.setValorTotal(valorTotal);
        marcacao.setValorAdiantamento(VALOR_ADIANTAMENTO_PADRAO);
        marcacao.setMetodoPagamento(request.getMetodoPagamento());
        marcacao.setReferenciaPagamento(request.getReferenciaPagamento());
        marcacao.setAdiantamentoPago(false);
        marcacao.setProfissional(escolhida != null ? escolhida
                : agendaService.escolherProfissional(request.getData(), request.getHora(), duracaoMinutos, null).orElse(null));
        marcacao.setObservacoes(request.getObservacoes() != null && !request.getObservacoes().isBlank()
                ? request.getObservacoes().trim() : null);

        for (Servico servico : servicos) {
            MarcacaoServico item = new MarcacaoServico();
            item.setServico(servico);
            item.setNomeServico(servico.getNome());
            item.setPrecoNoMomento(servico.getPreco());
            marcacao.adicionarServico(item);
        }

        Marcacao salva = marcacaoRepository.save(marcacao);
        eventPublisher.publishEvent(new NotificacaoMarcacaoEvent(salva.getId(), NotificacaoMarcacaoEvent.Tipo.RECEBIDA, true));
        return MarcacaoResponse.deEntidade(salva);
    }

    private Usuario obterOuCriarCliente(CriarMarcacaoRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RegraNegocioException("Email é obrigatório para concluir a reserva.");
        }

        // Sem sessão não há como provar que o email pertence a quem está a reservar.
        if (usuarioRepository.existsByEmailAndDeletadoFalse(request.getEmail())) {
            throw new RegraNegocioException("Já existe uma conta com este email. Inicie sessão para concluir a reserva.");
        }
        if (request.getSenha() == null || request.getSenha().isBlank()) {
            throw new RegraNegocioException("Defina uma palavra-passe para criar a sua conta e concluir a reserva.");
        }
        if (request.getNome() == null || request.getNome().isBlank()) {
            throw new RegraNegocioException("Nome é obrigatório para concluir a reserva.");
        }
        if (request.getTelefone() == null || request.getTelefone().isBlank()) {
            throw new RegraNegocioException("Telefone é obrigatório para concluir a reserva.");
        }

        Usuario novoCliente = new Usuario();
        novoCliente.setNome(request.getNome());
        novoCliente.setEmail(request.getEmail());
        novoCliente.setTelefone(request.getTelefone());
        novoCliente.setSenhaHash(passwordEncoder.encode(request.getSenha()));
        novoCliente.setPapel(PapelUsuario.CLIENTE);
        moradaResolver.aplicar(novoCliente, request.getProvinciaId(), request.getDistritoId());
        Usuario salvo = usuarioRepository.save(novoCliente);
        eventPublisher.publishEvent(new mz.netlineserv.chuangastudio.modules.notifications.event.NovoClienteEvent(salvo.getId()));
        return salvo;
    }

    @Transactional(readOnly = true)
    public PageResponse<MarcacaoResponse> listarDoClienteOnline(Pageable pageable) {
        Usuario cliente = usuarioService.obterUsuarioOnline();
        Specification<Marcacao> spec = BaseSpecification.of(Marcacao.class).equals("cliente.id", cliente.getId()).build();
        return PageResponse.from(marcacaoRepository.findAll(spec, pageable).map(MarcacaoResponse::deEntidade));
    }

    @Transactional(readOnly = true)
    public PageResponse<MarcacaoResponse> listarAdmin(FiltroMarcacoes filtro, Pageable pageable) {
        Specification<Marcacao> spec = BaseSpecification.of(Marcacao.class)
                .equals("status", filtro.status())
                .likeAny(filtro.busca(), "cliente.nome", "cliente.telefone", "cliente.email")
                .equals("profissional.id", filtro.profissionalId())
                .isNull("profissional", filtro.semProfissional())
                .equals("cliente.id", filtro.clienteId())
                .between("dataMarcacao", filtro.dataInicio(), filtro.dataFim())
                .build();
        Page<MarcacaoResponse> pagina = marcacaoRepository.findAll(spec, pageable).map(MarcacaoResponse::deEntidade);
        enriquecer(pagina.getContent());
        return PageResponse.from(pagina);
    }

    @Transactional(readOnly = true)
    public MarcacaoResponse obterAdmin(UUID id) {
        MarcacaoResponse resposta = MarcacaoResponse.deEntidade(buscarPorId(id));
        enriquecer(List.of(resposta));
        return resposta;
    }

    /** Fatura e nº de faltas da cliente para as linhas da página, com duas queries no total. */
    private void enriquecer(List<MarcacaoResponse> linhas) {
        if (linhas.isEmpty()) {
            return;
        }
        Map<UUID, Fatura> faturas = faturaRepository.buscarValidasDasMarcacoes(linhas.stream().map(MarcacaoResponse::getId).toList())
                .stream().collect(Collectors.toMap(f -> f.getMarcacao().getId(), f -> f, (a, b) -> a));
        Map<UUID, Long> faltas = marcacaoRepository.resumoPorCliente(linhas.stream().map(MarcacaoResponse::getClienteId).distinct().toList())
                .stream().collect(Collectors.toMap(r -> (UUID) r[0], r -> ((Number) r[3]).longValue()));
        for (MarcacaoResponse linha : linhas) {
            Fatura fatura = faturas.get(linha.getId());
            if (fatura != null) {
                linha.setFaturaId(fatura.getId());
                linha.setFaturaNumero(fatura.getNumero());
                linha.setFaturaStatus(fatura.getStatus().name());
            }
            linha.setClienteFaltas(faltas.getOrDefault(linha.getClienteId(), 0L));
        }
    }

    @Transactional
    public MarcacaoResponse atualizarStatus(UUID id, StatusMarcacao novoStatus) {
        Marcacao marcacao = buscarPorId(id);
        if (marcacao.getStatus() == novoStatus) {
            return MarcacaoResponse.deEntidade(marcacao);
        }
        if (!marcacao.getStatus().podeTransitarPara(novoStatus)) {
            throw new RegraNegocioException("Não é possível mudar uma marcação de "
                    + marcacao.getStatus() + " para " + novoStatus + ".");
        }
        if (novoStatus == StatusMarcacao.NAO_COMPARECEU
                && LocalDateTime.of(marcacao.getDataMarcacao(), marcacao.getHoraMarcacao()).isAfter(LocalDateTime.now(FUSO_ESTUDIO))) {
            throw new RegraNegocioException("Só pode marcar falta depois da hora da marcação.");
        }
        marcacao.setStatus(novoStatus);
        if (novoStatus == StatusMarcacao.CONFIRMADA) {
            notificar(marcacao, NotificacaoMarcacaoEvent.Tipo.CONFIRMADA, false);
        } else if (novoStatus == StatusMarcacao.CANCELADA) {
            notificar(marcacao, NotificacaoMarcacaoEvent.Tipo.CANCELADA, false);
        }
        return MarcacaoResponse.deEntidade(marcacaoRepository.save(marcacao));
    }

    @Transactional
    public MarcacaoResponse confirmarPagamento(UUID id) {
        Marcacao marcacao = buscarPorId(id);
        if (!marcacao.getStatus().isAtiva()) {
            throw new RegraNegocioException("Não é possível confirmar o pagamento de uma marcação "
                    + marcacao.getStatus() + ".");
        }
        marcacao.setAdiantamentoPago(true);
        if (marcacao.getStatus() == StatusMarcacao.AGUARDA_CONFIRMACAO) {
            marcacao.setStatus(StatusMarcacao.CONFIRMADA);
            notificar(marcacao, NotificacaoMarcacaoEvent.Tipo.CONFIRMADA, false);
        }
        return MarcacaoResponse.deEntidade(marcacaoRepository.save(marcacao));
    }

    @Transactional
    public MarcacaoResponse cancelarDoClienteOnline(UUID id) {
        Usuario cliente = usuarioService.obterUsuarioOnline();
        Marcacao marcacao = buscarPorId(id);
        if (!marcacao.getCliente().getId().equals(cliente.getId())) {
            throw new AcessoNegadoException("Esta marcação não pertence ao utilizador autenticado.");
        }
        if (!marcacao.getStatus().isAtiva()) {
            throw new RegraNegocioException("Só é possível cancelar marcações que aguardam confirmação ou estão confirmadas.");
        }
        marcacao.setStatus(StatusMarcacao.CANCELADA);
        // Cancelado pelo próprio cliente: o estúdio precisa de saber que o horário ficou livre.
        notificar(marcacao, NotificacaoMarcacaoEvent.Tipo.CANCELADA, true);
        return MarcacaoResponse.deEntidade(marcacaoRepository.save(marcacao));
    }

    /** Pela cliente: só marcações ativas e com antecedência mínima (app.marcacoes.antecedencia-remarcar-horas). */
    @Transactional
    public MarcacaoResponse remarcarDoClienteOnline(UUID id, LocalDate data, LocalTime hora) {
        Usuario cliente = usuarioService.obterUsuarioOnline();
        Marcacao marcacao = buscarPorId(id);
        if (!marcacao.getCliente().getId().equals(cliente.getId())) {
            throw new AcessoNegadoException("Esta marcação não pertence ao utilizador autenticado.");
        }
        LocalDateTime atual = LocalDateTime.of(marcacao.getDataMarcacao(), marcacao.getHoraMarcacao());
        if (atual.isBefore(LocalDateTime.now(FUSO_ESTUDIO).plusHours(antecedenciaRemarcarHoras))) {
            throw new RegraNegocioException("Só é possível remarcar até " + antecedenciaRemarcarHoras
                    + " horas antes. Fale connosco pelo WhatsApp.");
        }
        // Mudança feita pela cliente: o estúdio precisa de saber.
        return remarcar(marcacao, data, hora, true);
    }

    /** Pelo estúdio: muda quem faz o atendimento (nulo = sem profissional). */
    @Transactional
    public MarcacaoResponse atribuirProfissional(UUID id, UUID profissionalId) {
        Marcacao marcacao = buscarPorId(id);
        if (!marcacao.getStatus().isAtiva()) {
            throw new RegraNegocioException("Só é possível mudar a profissional de marcações ativas.");
        }
        if (profissionalId == null) {
            marcacao.setProfissional(null);
        } else {
            Profissional profissional = profissionalAtiva(profissionalId);
            int duracao = marcacao.getServicos().stream().mapToInt(i -> i.getServico().getDuracaoMinutos()).sum();
            agendaService.validarMarcacao(marcacao.getDataMarcacao(), marcacao.getHoraMarcacao(), duracao,
                    marcacao.getId(), profissional.getId());
            marcacao.setProfissional(profissional);
        }
        return MarcacaoResponse.deEntidade(marcacaoRepository.save(marcacao));
    }

    /** Pelo estúdio: sem antecedência mínima. */
    @Transactional
    public MarcacaoResponse remarcarAdmin(UUID id, LocalDate data, LocalTime hora) {
        return remarcar(buscarPorId(id), data, hora, false);
    }

    private MarcacaoResponse remarcar(Marcacao marcacao, LocalDate data, LocalTime hora, boolean avisarEstudio) {
        if (!marcacao.getStatus().isAtiva()) {
            throw new RegraNegocioException("Só é possível remarcar marcações que aguardam confirmação ou estão confirmadas.");
        }
        LocalDateTime anterior = LocalDateTime.of(marcacao.getDataMarcacao(), marcacao.getHoraMarcacao());
        if (anterior.equals(LocalDateTime.of(data, hora))) {
            return MarcacaoResponse.deEntidade(marcacao);
        }
        int duracao = marcacao.getServicos().stream().mapToInt(i -> i.getServico().getDuracaoMinutos()).sum();
        agendaService.validarMarcacao(data, hora, duracao, marcacao.getId(), null);

        // Mantém a mesma profissional se estiver livre na nova hora; senão, a primeira livre.
        Profissional atual = marcacao.getProfissional();
        boolean atualLivre = false;
        if (atual != null && atual.isAtivo()) {
            try {
                agendaService.validarMarcacao(data, hora, duracao, marcacao.getId(), atual.getId());
                atualLivre = true;
            } catch (RegraNegocioException ignorada) {
                // fica para a escolha automática
            }
        }
        if (!atualLivre) {
            marcacao.setProfissional(agendaService.escolherProfissional(data, hora, duracao, marcacao.getId()).orElse(null));
        }
        marcacao.setDataMarcacao(data);
        marcacao.setHoraMarcacao(hora);
        marcacao.setLembreteEnviadoEm(null);
        Marcacao salva = marcacaoRepository.save(marcacao);
        eventPublisher.publishEvent(new NotificacaoMarcacaoEvent(salva.getId(), NotificacaoMarcacaoEvent.Tipo.REMARCADA,
                avisarEstudio, anterior));
        return MarcacaoResponse.deEntidade(salva);
    }

    @Transactional
    public void eliminar(UUID id) {
        Marcacao marcacao = buscarPorId(id);
        // Soft delete, como nos serviços: mantém o histórico e a receita auditáveis.
        marcacao.marcarComoDeletado();
        marcacaoRepository.save(marcacao);
    }

    private void notificar(Marcacao marcacao, NotificacaoMarcacaoEvent.Tipo tipo, boolean avisarEstudio) {
        eventPublisher.publishEvent(new NotificacaoMarcacaoEvent(marcacao.getId(), tipo, avisarEstudio));
    }

    private Profissional profissionalAtiva(UUID id) {
        return profissionalRepository.findById(id)
                .filter(p -> !p.isDeletado() && p.isAtivo())
                .orElseThrow(() -> new RegraNegocioException("Profissional não encontrada ou inativa."));
    }

    private Marcacao buscarPorId(UUID id) {
        return marcacaoRepository.findById(id)
                .filter(m -> !m.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Marcação não encontrada."));
    }
}