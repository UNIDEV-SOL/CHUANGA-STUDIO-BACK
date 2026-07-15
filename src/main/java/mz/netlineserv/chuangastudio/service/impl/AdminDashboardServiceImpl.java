package mz.netlineserv.chuangastudio.service.impl;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.domain.entity.Marcacao;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;
import mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao;
import mz.netlineserv.chuangastudio.dto.response.ClienteResumoResponse;
import mz.netlineserv.chuangastudio.dto.response.EstatisticasAdminResponse;
import mz.netlineserv.chuangastudio.dto.response.MarcacaoResponse;
import mz.netlineserv.chuangastudio.repository.CotacaoRepository;
import mz.netlineserv.chuangastudio.repository.MarcacaoRepository;
import mz.netlineserv.chuangastudio.repository.UsuarioRepository;
import mz.netlineserv.chuangastudio.service.AdminDashboardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int LIMITE_ATIVIDADE_RECENTE = 10;
    private static final int LIMITE_PROXIMAS_MARCACOES = 10;

    private final MarcacaoRepository marcacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CotacaoRepository cotacaoRepository;

    @Override
    public EstatisticasAdminResponse obterEstatisticas() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate fimMes = hoje.with(TemporalAdjusters.lastDayOfMonth());

        long marcacoesHoje = marcacaoRepository.countByDataMarcacaoAndDeletadoFalse(hoje);
        BigDecimal receitaDoMes = marcacaoRepository.somarReceitaEntre(inicioMes, fimMes);
        long totalClientes = usuarioRepository.findByPapelAndDeletadoFalseOrderByCriadoEmDesc(PapelUsuario.CLIENTE).size();
        long totalCotacoes = cotacaoRepository.countByDeletadoFalse();

        return new EstatisticasAdminResponse(marcacoesHoje, receitaDoMes, totalClientes, totalCotacoes);
    }

    @Override
    public List<MarcacaoResponse> proximasMarcacoes() {
        LocalDate hoje = LocalDate.now();
        return marcacaoRepository.findByDeletadoFalseOrderByDataMarcacaoDescHoraMarcacaoDesc().stream()
                .filter(m -> !m.getDataMarcacao().isBefore(hoje))
                .filter(m -> m.getStatus() == StatusMarcacao.AGUARDA_CONFIRMACAO || m.getStatus() == StatusMarcacao.CONFIRMADA)
                .sorted(Comparator.comparing(mz.netlineserv.chuangastudio.domain.entity.Marcacao::getDataMarcacao)
                        .thenComparing(mz.netlineserv.chuangastudio.domain.entity.Marcacao::getHoraMarcacao))
                .limit(LIMITE_PROXIMAS_MARCACOES)
                .map(MarcacaoResponse::deEntidade)
                .collect(Collectors.toList());
    }

    @Override
    public List<MarcacaoResponse> atividadeRecente() {
        return marcacaoRepository.findByDeletadoFalseOrderByDataMarcacaoDescHoraMarcacaoDesc().stream()
                .sorted(Comparator.comparing(
                        mz.netlineserv.chuangastudio.domain.entity.Marcacao::getCriadoEm).reversed())
                .limit(LIMITE_ATIVIDADE_RECENTE)
                .map(MarcacaoResponse::deEntidade)
                .collect(Collectors.toList());
    }

    @Override
    public List<ClienteResumoResponse> listarClientesComResumo() {
        List<Usuario> clientes = usuarioRepository.findByPapelAndDeletadoFalseOrderByCriadoEmDesc(PapelUsuario.CLIENTE);
        List<Marcacao> todasMarcacoes = marcacaoRepository.findByDeletadoFalseOrderByDataMarcacaoDescHoraMarcacaoDesc();

        return clientes.stream().map(cliente -> {
            List<Marcacao> marcacoesDoCliente = todasMarcacoes.stream()
                    .filter(m -> m.getCliente().getId().equals(cliente.getId()))
                    .collect(Collectors.toList());

            BigDecimal totalInvestido = marcacoesDoCliente.stream()
                    .filter(m -> m.getStatus() != StatusMarcacao.CANCELADA)
                    .map(Marcacao::getValorTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return new ClienteResumoResponse(
                    cliente.getId(),
                    cliente.getNome(),
                    cliente.getEmail(),
                    cliente.getTelefone(),
                    cliente.getCriadoEm(),
                    marcacoesDoCliente.size(),
                    totalInvestido
            );
        }).collect(Collectors.toList());
    }
}
