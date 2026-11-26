package mz.netlineserv.chuangastudio.modules.invoices.service;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.exceptions.AcessoNegadoException;
import mz.netlineserv.chuangastudio.shared.exceptions.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.shared.exceptions.RegraNegocioException;
import mz.netlineserv.chuangastudio.modules.invoices.domain.FormaPagamento;
import mz.netlineserv.chuangastudio.modules.invoices.domain.StatusFatura;
import mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao;
import mz.netlineserv.chuangastudio.modules.invoices.request.EmitirFaturaRequest;
import mz.netlineserv.chuangastudio.modules.invoices.request.RegistarPagamentoFaturaRequest;
import mz.netlineserv.chuangastudio.modules.invoices.vm.FaturaResponse;
import mz.netlineserv.chuangastudio.shared.config.DadosEstudio;
import mz.netlineserv.chuangastudio.modules.invoices.pdf.FaturaPdfRenderer;
import mz.netlineserv.chuangastudio.modules.notifications.event.NotificacaoFaturaEvent;
import mz.netlineserv.chuangastudio.modules.invoices.repository.FaturaRepository;
import mz.netlineserv.chuangastudio.modules.bookings.repository.MarcacaoRepository;
import mz.netlineserv.chuangastudio.modules.catalog.repository.ServicoRepository;
import mz.netlineserv.chuangastudio.modules.users.service.UsuarioService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import mz.netlineserv.chuangastudio.shared.persistence.BaseSpecification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import mz.netlineserv.chuangastudio.modules.bookings.domain.Marcacao;
import mz.netlineserv.chuangastudio.modules.bookings.domain.MarcacaoServico;
import mz.netlineserv.chuangastudio.modules.catalog.domain.Servico;
import mz.netlineserv.chuangastudio.modules.invoices.domain.Fatura;
import mz.netlineserv.chuangastudio.modules.invoices.domain.FaturaItem;
import mz.netlineserv.chuangastudio.modules.users.domain.Usuario;

@Service
@RequiredArgsConstructor
public class FaturaService {

    public record Pdf(String nomeFicheiro, byte[] conteudo) {
        }

    private static final ZoneId FUSO_ESTUDIO = ZoneId.of("Africa/Maputo");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final FaturaRepository faturaRepository;
    private final MarcacaoRepository marcacaoRepository;
    private final ServicoRepository servicoRepository;
    private final UsuarioService usuarioService;
    private final FaturaPdfRenderer faturaPdfRenderer;
    private final DadosEstudio dadosEstudio;
    private final mz.netlineserv.chuangastudio.modules.loyalty.service.FidelizacaoService fidelizacaoService;
    private final mz.netlineserv.chuangastudio.modules.loyalty.service.ValeService valeService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FaturaResponse emitir(UUID marcacaoId, EmitirFaturaRequest request) {
        Marcacao marcacao = marcacaoRepository.buscarPorIdComDetalhes(marcacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Marcação não encontrada."));
        if (!marcacao.getStatus().isAtiva()) {
            throw new RegraNegocioException("Só é possível faturar marcações que aguardam confirmação ou estão confirmadas.");
        }
        if (faturaRepository.existsByMarcacaoIdAndStatusNotAndDeletadoFalse(marcacaoId, StatusFatura.ANULADA)) {
            throw new RegraNegocioException("Esta marcação já tem uma fatura. Anule-a para emitir outra.");
        }

        LocalDateTime agora = LocalDateTime.now(FUSO_ESTUDIO);
        Fatura fatura = new Fatura();
        fatura.setMarcacao(marcacao);
        fatura.setNumero("FT " + agora.getYear() + "/" + String.format("%04d", faturaRepository.proximoNumero()));
        fatura.setDataEmissao(agora);
        fatura.setTokenPublico(novoToken());
        fatura.setObservacoes(textoOuNulo(request.getObservacoes()));
        fatura.setClienteNuit(textoOuNulo(request.getClienteNuit()));
        request.getItens().forEach(item -> fatura.adicionarItem(criarItem(item)));

        BigDecimal subtotal = fatura.getItens().stream().map(FaturaItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal desconto = (request.getDesconto() != null ? request.getDesconto() : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        if (desconto.compareTo(subtotal) > 0) {
            throw new RegraNegocioException("O desconto não pode ser maior que o subtotal.");
        }
        BigDecimal aposDesconto = subtotal.subtract(desconto);
        // Recompensa de fidelização (desconto), antes do adiantamento.
        BigDecimal fidelizacao = request.isAplicarFidelizacao()
                ? fidelizacaoService.valorParaAplicar(marcacao.getCliente().getId()).min(aposDesconto)
                : BigDecimal.ZERO;
        BigDecimal aposFidelizacao = aposDesconto.subtract(fidelizacao);
        // O adiantamento só abate se foi mesmo recebido; se cobrir tudo, não há troco a devolver aqui.
        BigDecimal adiantamento = marcacao.isAdiantamentoPago()
                ? marcacao.getValorAdiantamento().min(aposFidelizacao)
                : BigDecimal.ZERO;
        BigDecimal restante = aposFidelizacao.subtract(adiantamento);
        // O vale paga o que falta, até ao saldo que tiver.
        BigDecimal valorVale = BigDecimal.ZERO;
        if (textoOuNulo(request.getCodigoVale()) != null) {
            var vale = valeService.validarParaUso(request.getCodigoVale());
            valorVale = vale.getSaldo().min(restante);
            if (valorVale.signum() > 0) {
                valeService.usar(vale, valorVale);
                fatura.setVale(vale);
            }
        }
        fatura.setSubtotal(subtotal);
        fatura.setDesconto(desconto);
        fatura.setDescontoFidelizacao(fidelizacao);
        fatura.setAdiantamentoDeduzido(adiantamento);
        fatura.setValorVale(valorVale);
        fatura.setTotalAPagar(restante.subtract(valorVale));

        if (request.getFormaPagamento() != null) {
            marcarComoPaga(fatura, request.getFormaPagamento(), request.getReferenciaPagamento(), agora);
        } else if (fatura.getTotalAPagar().signum() == 0) {
            fatura.setStatus(StatusFatura.PAGA);
            fatura.setPagoEm(agora);
        }

        // Atendimento terminado: a receita passa a ser o valor realmente faturado.
        marcacao.setStatus(StatusMarcacao.CONCLUIDA);
        marcacao.setValorTotal(aposFidelizacao);

        Fatura salva = faturaRepository.save(fatura);
        eventPublisher.publishEvent(new NotificacaoFaturaEvent(salva.getId()));
        return paraResposta(salva);
    }

    @Transactional
    public FaturaResponse registarPagamento(UUID id, RegistarPagamentoFaturaRequest request) {
        Fatura fatura = buscar(id);
        if (fatura.getStatus() != StatusFatura.PENDENTE) {
            throw new RegraNegocioException("Só é possível registar o pagamento de faturas por pagar.");
        }
        marcarComoPaga(fatura, request.getFormaPagamento(), request.getReferenciaPagamento(), LocalDateTime.now(FUSO_ESTUDIO));
        eventPublisher.publishEvent(new NotificacaoFaturaEvent(fatura.getId()));
        return paraResposta(faturaRepository.save(fatura));
    }

    @Transactional
    public FaturaResponse anular(UUID id) {
        Fatura fatura = buscar(id);
        if (fatura.getStatus() == StatusFatura.ANULADA) {
            throw new RegraNegocioException("Esta fatura já está anulada.");
        }
        fatura.setStatus(StatusFatura.ANULADA);
        // O vale recupera o que tinha pago; a recompensa de fidelização volta a ficar disponível (é calculada).
        if (fatura.getVale() != null && fatura.getValorVale().signum() > 0) {
            valeService.devolver(fatura.getVale(), fatura.getValorVale());
        }

        // Reabre a marcação para se poder emitir a fatura correta.
        Marcacao marcacao = fatura.getMarcacao();
        marcacao.setStatus(StatusMarcacao.CONFIRMADA);
        marcacao.setValorTotal(marcacao.getServicos().stream()
                .map(MarcacaoServico::getPrecoNoMomento)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return paraResposta(faturaRepository.save(fatura));
    }

    @Transactional
    public void reenviar(UUID id, String email) {
        Fatura fatura = buscar(id);
        if (fatura.getStatus() == StatusFatura.ANULADA) {
            throw new RegraNegocioException("Não é possível enviar uma fatura anulada.");
        }
        String destino = textoOuNulo(email);
        if (destino == null && textoOuNulo(fatura.getMarcacao().getCliente().getEmail()) == null) {
            throw new RegraNegocioException("O cliente não tem email. Indique um email para reenviar.");
        }
        eventPublisher.publishEvent(new NotificacaoFaturaEvent(fatura.getId(), destino, false));
    }

    @Transactional(readOnly = true)
    public PageResponse<FaturaResponse> listarAdmin(StatusFatura status, String busca, UUID clienteId,
                                                   LocalDate dataInicio, LocalDate dataFim, Pageable pageable) {
        Specification<Fatura> spec = BaseSpecification.of(Fatura.class)
                .equals("status", status)
                .likeAny(busca, "numero", "marcacao.cliente.nome", "marcacao.cliente.telefone")
                .equals("marcacao.cliente.id", clienteId)
                .and(dataInicio == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("dataEmissao"), dataInicio.atStartOfDay()))
                .and(dataFim == null ? null : (root, q, cb) -> cb.lessThan(root.get("dataEmissao"), dataFim.plusDays(1).atStartOfDay()))
                .build();
        return PageResponse.from(faturaRepository.findAll(spec, pageable).map(this::paraResposta));
    }

    @Transactional(readOnly = true)
    public PageResponse<FaturaResponse> listarDoClienteOnline(Pageable pageable) {
        Usuario cliente = usuarioService.obterUsuarioOnline();
        Specification<Fatura> spec = BaseSpecification.of(Fatura.class)
                .equals("marcacao.cliente.id", cliente.getId())
                .and((root, q, cb) -> cb.notEqual(root.get("status"), StatusFatura.ANULADA))
                .build();
        return PageResponse.from(faturaRepository.findAll(spec, pageable).map(this::paraResposta));
    }

    @Transactional(readOnly = true)
    public FaturaResponse obterAdmin(UUID id) {
        return paraResposta(buscar(id));
    }

    @Transactional(readOnly = true)
    public Pdf pdfAdmin(UUID id) {
        return pdf(buscar(id));
    }

    @Transactional(readOnly = true)
    public Pdf pdfDoClienteOnline(UUID id) {
        Usuario cliente = usuarioService.obterUsuarioOnline();
        Fatura fatura = buscar(id);
        if (!fatura.getMarcacao().getCliente().getId().equals(cliente.getId())) {
            throw new AcessoNegadoException("Esta fatura não pertence ao utilizador autenticado.");
        }
        return pdf(fatura);
    }

    @Transactional(readOnly = true)
    public Pdf pdfPublico(String token) {
        return pdf(faturaRepository.buscarPorTokenComDetalhes(token)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fatura não encontrada.")));
    }

    private FaturaItem criarItem(EmitirFaturaRequest.Item pedido) {
        FaturaItem item = new FaturaItem();
        if (pedido.getServicoId() != null) {
            Servico servico = servicoRepository.findById(pedido.getServicoId())
                    .filter(s -> !s.isDeletado())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado."));
            item.setServico(servico);
            item.setDescricao(servico.getNome());
            item.setPrecoUnitario(pedido.getPrecoUnitario() != null ? pedido.getPrecoUnitario() : servico.getPreco());
        } else {
            if (textoOuNulo(pedido.getDescricao()) == null || pedido.getPrecoUnitario() == null) {
                throw new RegraNegocioException("Cada extra precisa de descrição e preço.");
            }
            item.setDescricao(pedido.getDescricao().trim());
            item.setPrecoUnitario(pedido.getPrecoUnitario());
        }
        int quantidade = pedido.getQuantidade() != null ? pedido.getQuantidade() : 1;
        item.setQuantidade(quantidade);
        item.setTotal(item.getPrecoUnitario().multiply(BigDecimal.valueOf(quantidade)));
        return item;
    }

    private static void marcarComoPaga(Fatura fatura, FormaPagamento forma,
                                       String referencia, LocalDateTime quando) {
        fatura.setStatus(StatusFatura.PAGA);
        fatura.setFormaPagamento(forma);
        fatura.setReferenciaPagamento(textoOuNulo(referencia));
        fatura.setPagoEm(quando);
    }

    private Pdf pdf(Fatura fatura) {
        return new Pdf(FaturaPdfRenderer.nomeFicheiro(fatura), faturaPdfRenderer.gerar(fatura));
    }

    private FaturaResponse paraResposta(Fatura fatura) {
        return FaturaResponse.deEntidade(fatura, dadosEstudio.urlPdfFatura(fatura.getTokenPublico()));
    }

    private Fatura buscar(UUID id) {
        return faturaRepository.buscarPorIdComDetalhes(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fatura não encontrada."));
    }

    private static String novoToken() {
        byte[] bytes = new byte[24];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
