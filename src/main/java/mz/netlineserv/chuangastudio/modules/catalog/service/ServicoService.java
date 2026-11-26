package mz.netlineserv.chuangastudio.modules.catalog.service;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.exceptions.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.modules.catalog.domain.Servico;
import mz.netlineserv.chuangastudio.modules.catalog.request.ServicoRequest;
import mz.netlineserv.chuangastudio.modules.catalog.vm.ServicoResponse;
import mz.netlineserv.chuangastudio.modules.catalog.repository.ServicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import mz.netlineserv.chuangastudio.shared.persistence.BaseSpecification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicoService {

    private final ServicoRepository servicoRepository;

    public PageResponse<ServicoResponse> listarPublico(String busca, String categoria, Pageable pageable) {
        return listarAdmin(busca, categoria, true, pageable);
    }

    public PageResponse<ServicoResponse> listarAdmin(String busca, String categoria, Boolean ativo, Pageable pageable) {
        Specification<Servico> spec = BaseSpecification.of(Servico.class)
                .likeAny(busca, "nome", "descricao")
                .equals("categoria", categoria)
                .equals("ativo", ativo)
                .build();
        return PageResponse.from(servicoRepository.findAll(spec, pageable).map(ServicoResponse::deEntidade));
    }

    @Transactional
    public ServicoResponse criar(ServicoRequest request) {
        Servico servico = new Servico();
        preencherDados(servico, request);
        return ServicoResponse.deEntidade(servicoRepository.save(servico));
    }

    @Transactional
    public ServicoResponse atualizar(UUID id, ServicoRequest request) {
        Servico servico = buscarPorId(id);
        preencherDados(servico, request);
        return ServicoResponse.deEntidade(servicoRepository.save(servico));
    }

    @Transactional
    public void eliminar(UUID id) {
        Servico servico = buscarPorId(id);
        servico.marcarComoDeletado();
        servicoRepository.save(servico);
    }

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
