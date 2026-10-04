package mz.netlineserv.chuangastudio.service.impl;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.exception.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.domain.entity.Cotacao;
import mz.netlineserv.chuangastudio.domain.entity.CotacaoServico;
import mz.netlineserv.chuangastudio.domain.entity.Servico;
import mz.netlineserv.chuangastudio.domain.enums.StatusCotacao;
import mz.netlineserv.chuangastudio.dto.request.CriarCotacaoRequest;
import mz.netlineserv.chuangastudio.dto.response.CotacaoResponse;
import mz.netlineserv.chuangastudio.notificacao.NotificacaoCotacaoEvent;
import mz.netlineserv.chuangastudio.repository.CotacaoRepository;
import mz.netlineserv.chuangastudio.repository.ServicoRepository;
import mz.netlineserv.chuangastudio.service.CotacaoService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import mz.netlineserv.chuangastudio.common.PageResponse;
import mz.netlineserv.chuangastudio.repository.spec.BaseSpecification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CotacaoServiceImpl implements CotacaoService {

    private final CotacaoRepository cotacaoRepository;
    private final ServicoRepository servicoRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CotacaoResponse> listarAdmin(StatusCotacao status, String busca, Pageable pageable) {
        Specification<Cotacao> spec = BaseSpecification.of(Cotacao.class)
                .equals("status", status)
                .likeAny(busca, "nome", "telefone", "email")
                .build();
        return PageResponse.from(cotacaoRepository.findAll(spec, pageable).map(CotacaoResponse::deEntidade));
    }

    @Override
    @Transactional
    public CotacaoResponse marcarComoRespondido(UUID id) {
        Cotacao cotacao = buscarPorId(id);
        cotacao.setStatus(StatusCotacao.RESPONDIDO);
        return CotacaoResponse.deEntidade(cotacaoRepository.save(cotacao));
    }

    @Override
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
