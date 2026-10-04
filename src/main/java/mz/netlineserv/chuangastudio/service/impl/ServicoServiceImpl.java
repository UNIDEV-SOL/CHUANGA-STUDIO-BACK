package mz.netlineserv.chuangastudio.service.impl;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.exception.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.domain.entity.Servico;
import mz.netlineserv.chuangastudio.dto.request.ServicoRequest;
import mz.netlineserv.chuangastudio.dto.response.ServicoResponse;
import mz.netlineserv.chuangastudio.repository.ServicoRepository;
import mz.netlineserv.chuangastudio.service.ServicoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mz.netlineserv.chuangastudio.common.PageResponse;
import mz.netlineserv.chuangastudio.repository.spec.BaseSpecification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicoServiceImpl implements ServicoService {

    private final ServicoRepository servicoRepository;

    @Override
    public PageResponse<ServicoResponse> listarPublico(String busca, String categoria, Pageable pageable) {
        return listarAdmin(busca, categoria, true, pageable);
    }

    @Override
    public PageResponse<ServicoResponse> listarAdmin(String busca, String categoria, Boolean ativo, Pageable pageable) {
        Specification<Servico> spec = BaseSpecification.of(Servico.class)
                .likeAny(busca, "nome", "descricao")
                .equals("categoria", categoria)
                .equals("ativo", ativo)
                .build();
        return PageResponse.from(servicoRepository.findAll(spec, pageable).map(ServicoResponse::deEntidade));
    }

    @Override
    @Transactional
    public ServicoResponse criar(ServicoRequest request) {
        Servico servico = new Servico();
        preencherDados(servico, request);
        return ServicoResponse.deEntidade(servicoRepository.save(servico));
    }

    @Override
    @Transactional
    public ServicoResponse atualizar(UUID id, ServicoRequest request) {
        Servico servico = buscarPorId(id);
        preencherDados(servico, request);
        return ServicoResponse.deEntidade(servicoRepository.save(servico));
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        Servico servico = buscarPorId(id);
        servico.marcarComoDeletado();
        servicoRepository.save(servico);
    }

    @Override
    public Servico buscarPorId(UUID id) {
        return servicoRepository.findById(id)
                .filter(servico -> !servico.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado."));
    }

    private void preencherDados(Servico servico, ServicoRequest request) {
        servico.setNome(request.getNome());
        servico.setCategoria(request.getCategoria());
        servico.setDescricao(request.getDescricao());
        servico.setPreco(request.getPreco());
        servico.setDuracaoMinutos(request.getDuracaoMinutos());
        servico.setDestaque(request.isDestaque());
        servico.setAtivo(request.isAtivo());
        servico.setManutencaoDias(request.getManutencaoDias());
    }
}
