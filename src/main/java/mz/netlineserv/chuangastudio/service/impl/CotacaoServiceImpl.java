package mz.netlineserv.chuangastudio.service.impl;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.exception.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.domain.entity.Cotacao;
import mz.netlineserv.chuangastudio.domain.entity.CotacaoServico;
import mz.netlineserv.chuangastudio.domain.entity.Servico;
import mz.netlineserv.chuangastudio.domain.enums.StatusCotacao;
import mz.netlineserv.chuangastudio.dto.request.CriarCotacaoRequest;
import mz.netlineserv.chuangastudio.dto.response.CotacaoResponse;
import mz.netlineserv.chuangastudio.repository.CotacaoRepository;
import mz.netlineserv.chuangastudio.repository.ServicoRepository;
import mz.netlineserv.chuangastudio.service.CotacaoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CotacaoServiceImpl implements CotacaoService {

    private final CotacaoRepository cotacaoRepository;
    private final ServicoRepository servicoRepository;

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
        cotacao.setEmail(request.getEmail());
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

        return CotacaoResponse.deEntidade(cotacaoRepository.save(cotacao));
    }

    @Override
    public List<CotacaoResponse> listarAdmin(StatusCotacao status) {
        List<Cotacao> cotacoes = status != null
                ? cotacaoRepository.findByStatusAndDeletadoFalseOrderByCriadoEmDesc(status)
                : cotacaoRepository.findByDeletadoFalseOrderByCriadoEmDesc();

        return cotacoes.stream().map(CotacaoResponse::deEntidade).collect(Collectors.toList());
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
