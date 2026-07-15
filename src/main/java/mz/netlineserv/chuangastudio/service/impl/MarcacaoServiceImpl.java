package mz.netlineserv.chuangastudio.service.impl;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.exception.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import mz.netlineserv.chuangastudio.domain.entity.*;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;
import mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao;
import mz.netlineserv.chuangastudio.dto.request.CriarMarcacaoRequest;
import mz.netlineserv.chuangastudio.dto.response.MarcacaoResponse;
import mz.netlineserv.chuangastudio.repository.MarcacaoRepository;
import mz.netlineserv.chuangastudio.repository.ServicoRepository;
import mz.netlineserv.chuangastudio.repository.UsuarioRepository;
import mz.netlineserv.chuangastudio.service.MarcacaoService;
import mz.netlineserv.chuangastudio.service.UsuarioService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MarcacaoServiceImpl implements MarcacaoService {

    private static final BigDecimal VALOR_ADIANTAMENTO_PADRAO = new BigDecimal("150.00");
    private static final int TAMANHO_MINIMO_REFERENCIA = 4;

    private final MarcacaoRepository marcacaoRepository;
    private final ServicoRepository servicoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioService usuarioService;

    @Override
    @Transactional
    public MarcacaoResponse criar(CriarMarcacaoRequest request, boolean autenticado) {

        if (request.getReferenciaPagamento() == null
                || request.getReferenciaPagamento().trim().length() < TAMANHO_MINIMO_REFERENCIA) {
            throw new RegraNegocioException(
                    "Referência de pagamento inválida. Informe pelo menos " + TAMANHO_MINIMO_REFERENCIA + " caracteres.");
        }

        Usuario cliente = autenticado ? usuarioService.obterUsuarioOnline() : obterOuCriarCliente(request);

        List<Servico> servicos = servicoRepository.findAllById(request.getServicosIds());
        if (servicos.isEmpty() || servicos.size() != request.getServicosIds().size()) {
            throw new RecursoNaoEncontradoException("Um ou mais serviços selecionados não foram encontrados.");
        }

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

        for (Servico servico : servicos) {
            MarcacaoServico item = new MarcacaoServico();
            item.setServico(servico);
            item.setNomeServico(servico.getNome());
            item.setPrecoNoMomento(servico.getPreco());
            marcacao.adicionarServico(item);
        }

        return MarcacaoResponse.deEntidade(marcacaoRepository.save(marcacao));
    }

    private Usuario obterOuCriarCliente(CriarMarcacaoRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RegraNegocioException("Email é obrigatório para concluir a reserva.");
        }

        return usuarioRepository.findByEmailAndDeletadoFalse(request.getEmail())
                .orElseGet(() -> {
                    if (request.getSenha() == null || request.getSenha().length() < 6) {
                        throw new RegraNegocioException(
                                "Defina uma palavra-passe com pelo menos 6 caracteres para criar a sua conta.");
                    }
                    Usuario novoCliente = new Usuario();
                    novoCliente.setNome(request.getNome());
                    novoCliente.setEmail(request.getEmail());
                    novoCliente.setTelefone(request.getTelefone());
                    novoCliente.setSenhaHash(passwordEncoder.encode(request.getSenha()));
                    novoCliente.setPapel(PapelUsuario.CLIENTE);
                    return usuarioRepository.save(novoCliente);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarcacaoResponse> listarDoClienteOnline() {
        Usuario cliente = usuarioService.obterUsuarioOnline();
        return marcacaoRepository.buscarDoClienteComDetalhes(cliente.getId())
                .stream()
                .map(MarcacaoResponse::deEntidade)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarcacaoResponse> listarTodasAdmin(StatusMarcacao status, String busca) {
        List<Marcacao> marcacoes = status != null
                ? marcacaoRepository.findByStatusAndDeletadoFalseOrderByDataMarcacaoDescHoraMarcacaoDesc(status)
                : marcacaoRepository.findByDeletadoFalseOrderByDataMarcacaoDescHoraMarcacaoDesc();

        if (busca != null && !busca.isBlank()) {
            String termo = busca.trim().toLowerCase();
            marcacoes = marcacoes.stream()
                    .filter(m -> m.getCliente().getNome().toLowerCase().contains(termo)
                            || m.getCliente().getTelefone().toLowerCase().contains(termo))
                    .collect(Collectors.toList());
        }

        return marcacoes.stream().map(MarcacaoResponse::deEntidade).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MarcacaoResponse atualizarStatus(UUID id, StatusMarcacao novoStatus) {
        Marcacao marcacao = buscarPorId(id);
        marcacao.setStatus(novoStatus);
        return MarcacaoResponse.deEntidade(marcacaoRepository.save(marcacao));
    }

    @Override
    @Transactional
    public MarcacaoResponse confirmarPagamento(UUID id) {
        Marcacao marcacao = buscarPorId(id);
        marcacao.setAdiantamentoPago(true);
        if (marcacao.getStatus() == StatusMarcacao.AGUARDA_CONFIRMACAO) {
            marcacao.setStatus(StatusMarcacao.CONFIRMADA);
        }
        return MarcacaoResponse.deEntidade(marcacaoRepository.save(marcacao));
    }

    @Override
    @Transactional
    public MarcacaoResponse cancelar(UUID id) {
        Marcacao marcacao = buscarPorId(id);
        marcacao.setStatus(StatusMarcacao.CANCELADA);
        return MarcacaoResponse.deEntidade(marcacaoRepository.save(marcacao));
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        Marcacao marcacao = buscarPorId(id);
        marcacaoRepository.delete(marcacao);
    }

    private Marcacao buscarPorId(UUID id) {
        return marcacaoRepository.findById(id)
                .filter(m -> !m.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Marcação não encontrada."));
    }
}