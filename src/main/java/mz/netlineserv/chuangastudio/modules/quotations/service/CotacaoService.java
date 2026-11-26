package mz.netlineserv.chuangastudio.modules.quotations.service;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.exceptions.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.modules.quotations.domain.Cotacao;
import mz.netlineserv.chuangastudio.modules.quotations.domain.CotacaoServico;
import mz.netlineserv.chuangastudio.modules.catalog.domain.Servico;
import mz.netlineserv.chuangastudio.modules.quotations.domain.StatusCotacao;
import mz.netlineserv.chuangastudio.modules.quotations.request.CriarCotacaoRequest;
import mz.netlineserv.chuangastudio.modules.quotations.vm.CotacaoResponse;
import mz.netlineserv.chuangastudio.modules.notifications.event.NotificacaoCotacaoEvent;
import mz.netlineserv.chuangastudio.modules.quotations.repository.CotacaoRepository;
import mz.netlineserv.chuangastudio.modules.catalog.repository.ServicoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import mz.netlineserv.chuangastudio.shared.persistence.BaseSpecification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CotacaoService {

    private final CotacaoRepository cotacaoRepository;
    private final ServicoRepository servicoRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CotacaoResponse criar(CriarCotacaoRequest request) {
        List<Servico> servicos = servicoRepository.findAllById(request.getServicosIds());
        if (servicos.isEmpty() || servicos.size() != request.getServicosIds().size()) {
            throw new RecursoNaoEncontradoException("Um ou mais serviços selecionados não foram encontrados.");
        }

        BigDecimal totalEstimado = servicos.stream()
                .map(Servico::getPreco)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Cotacao cotacao = new Cotacao();
        cotacao.setNome(request.getNome());
        cotacao.setTelefone(request.getTelefone());
        cotacao.setEmail(request.getEmail() != null && !request.getEmail().isBlank() ? request.getEmail().trim() : null);
        cotacao.setMensagem(request.getMensagem());
        cotacao.setTotalEstimado(totalEstimado);
        cotacao.setStatus(StatusCotacao.NOVO);

        for (Servico servico : servicos) {
            CotacaoServico item = new CotacaoServico();
            item.setServico(servico);
            item.setNomeServico(servico.getNome());
            item.setPrecoNoMomento(servico.getPreco());
            cotacao.adicionarServico(item);
        }

        Cotacao salva = cotacaoRepository.save(cotacao);
        eventPublisher.publishEvent(new NotificacaoCotacaoEvent(salva.getId()));
        return CotacaoResponse.deEntidade(salva);
    }

    @Transactional(readOnly = true)
    public PageResponse<CotacaoResponse> listarAdmin(StatusCotacao status, String busca, Pageable pageable) {
        Specification<Cotacao> spec = BaseSpecification.of(Cotacao.class)
                .equals("status", status)
                .likeAny(busca, "nome", "telefone", "email")
                .build();
        return PageResponse.from(cotacaoRepository.findAll(spec, pageable).map(CotacaoResponse::deEntidade));
    }

    @Transactional
    public CotacaoResponse marcarComoRespondido(UUID id) {
        Cotacao cotacao = buscarPorId(id);
        cotacao.setStatus(StatusCotacao.RESPONDIDO);
        return CotacaoResponse.deEntidade(cotacaoRepository.save(cotacao));
    }

    @Transactional
    public CotacaoResponse arquivar(UUID id) {
        Cotacao cotacao = buscarPorId(id);
        cotacao.setStatus(StatusCotacao.ARQUIVADO);
        return CotacaoResponse.deEntidade(cotacaoRepository.save(cotacao));
    }

    private Cotacao buscarPorId(UUID id) {
        return cotacaoRepository.findById(id)
                .filter(c -> !c.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cotação não encontrada."));
    }
}
