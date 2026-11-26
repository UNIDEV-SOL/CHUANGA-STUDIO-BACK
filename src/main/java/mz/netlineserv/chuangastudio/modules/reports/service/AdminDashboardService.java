package mz.netlineserv.chuangastudio.modules.reports.service;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.modules.bookings.domain.Marcacao;
import mz.netlineserv.chuangastudio.modules.users.domain.Usuario;
import mz.netlineserv.chuangastudio.modules.users.domain.PapelUsuario;
import mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao;
import mz.netlineserv.chuangastudio.modules.clients.vm.ClienteResumoResponse;
import mz.netlineserv.chuangastudio.modules.reports.vm.EstatisticasAdminResponse;
import mz.netlineserv.chuangastudio.modules.bookings.vm.MarcacaoResponse;
import mz.netlineserv.chuangastudio.modules.quotations.repository.CotacaoRepository;
import mz.netlineserv.chuangastudio.modules.bookings.repository.MarcacaoRepository;
import mz.netlineserv.chuangastudio.modules.users.repository.UsuarioRepository;
import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import mz.netlineserv.chuangastudio.shared.persistence.BaseSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.ZoneId;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final ZoneId FUSO_ESTUDIO = ZoneId.of("Africa/Maputo");

    private final MarcacaoRepository marcacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CotacaoRepository cotacaoRepository;

    public EstatisticasAdminResponse obterEstatisticas() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate fimMes = hoje.with(TemporalAdjusters.lastDayOfMonth());

        long marcacoesHoje = marcacaoRepository.countByDataMarcacaoAndDeletadoFalse(hoje);
        BigDecimal receitaDoMes = marcacaoRepository.somarReceitaEntre(inicioMes, fimMes);
        long totalClientes = usuarioRepository.count(BaseSpecification.of(Usuario.class).equals("papel", PapelUsuario.CLIENTE).build());
        long totalCotacoes = cotacaoRepository.countByDeletadoFalse();

        return new EstatisticasAdminResponse(marcacoesHoje, receitaDoMes, totalClientes, totalCotacoes);
    }

    public PageResponse<MarcacaoResponse> proximasMarcacoes(Pageable pageable) {
        Specification<Marcacao> spec = BaseSpecification.of(Marcacao.class)
                .in("status", List.of(StatusMarcacao.AGUARDA_CONFIRMACAO, StatusMarcacao.CONFIRMADA))
                .between("dataMarcacao", LocalDate.now(FUSO_ESTUDIO), null)
                .build();
        Pageable ordenado = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("dataMarcacao", "horaMarcacao"));
        return PageResponse.from(marcacaoRepository.findAll(spec, ordenado).map(MarcacaoResponse::deEntidade));
    }

    public PageResponse<MarcacaoResponse> atividadeRecente(Pageable pageable) {
        Pageable ordenado = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "criadoEm"));
        return PageResponse.from(marcacaoRepository.findAll(BaseSpecification.of(Marcacao.class).build(), ordenado)
                .map(MarcacaoResponse::deEntidade));
    }

    public PageResponse<ClienteResumoResponse> listarClientes(String busca, Long provinciaId, Long distritoId, Pageable pageable) {
        Specification<Usuario> spec = BaseSpecification.of(Usuario.class)
                .equals("papel", PapelUsuario.CLIENTE)
                .likeAny(busca, "nome", "email", "telefone")
                .equals("provincia.id", provinciaId)
                .equals("distrito.id", distritoId)
                .build();
        Page<Usuario> pagina = usuarioRepository.findAll(spec, pageable);
        // Números de cada cliente da página numa só query agregada.
        Map<UUID, Object[]> resumo = pagina.isEmpty() ? Map.of()
                : marcacaoRepository.resumoPorCliente(pagina.map(Usuario::getId).getContent()).stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> r));
        return PageResponse.from(pagina.map(c -> {
            Object[] r = resumo.get(c.getId());
            return new ClienteResumoResponse(
                    c.getId(), c.getNome(), c.getEmail(), c.getTelefone(), c.getCriadoEm(),
                    c.getProvincia() != null ? c.getProvincia().getNome() : null,
                    c.getDistrito() != null ? c.getDistrito().getNome() : null,
                    r == null ? 0 : ((Number) r[1]).longValue(),
                    r == null ? BigDecimal.ZERO : (BigDecimal) r[2],
                    r == null ? 0 : ((Number) r[3]).longValue());
        }));
    }
}
