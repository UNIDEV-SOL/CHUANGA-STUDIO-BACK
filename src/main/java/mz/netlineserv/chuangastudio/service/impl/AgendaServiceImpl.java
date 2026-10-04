package mz.netlineserv.chuangastudio.service.impl;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.exception.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import mz.netlineserv.chuangastudio.domain.entity.DiaFechado;
import mz.netlineserv.chuangastudio.domain.entity.HorarioFuncionamento;
import mz.netlineserv.chuangastudio.domain.entity.Marcacao;
import mz.netlineserv.chuangastudio.domain.entity.Servico;
import mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao;
import mz.netlineserv.chuangastudio.dto.HorarioDiaDto;
import mz.netlineserv.chuangastudio.dto.request.AtualizarHorarioRequest;
import mz.netlineserv.chuangastudio.dto.request.DiaFechadoRequest;
import mz.netlineserv.chuangastudio.dto.response.ConfiguracaoHorarioResponse;
import mz.netlineserv.chuangastudio.dto.response.DisponibilidadeResponse;
import mz.netlineserv.chuangastudio.repository.DiaFechadoRepository;
import mz.netlineserv.chuangastudio.repository.HorarioFuncionamentoRepository;
import mz.netlineserv.chuangastudio.domain.entity.Profissional;
import mz.netlineserv.chuangastudio.repository.MarcacaoRepository;
import mz.netlineserv.chuangastudio.repository.ProfissionalRepository;
import mz.netlineserv.chuangastudio.repository.ServicoRepository;
import mz.netlineserv.chuangastudio.service.AgendaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgendaServiceImpl implements AgendaService {

    private static final ZoneId FUSO_ESTUDIO = ZoneId.of("Africa/Maputo");
    private static final Locale PORTUGUES = Locale.forLanguageTag("pt-PT");
    private static final List<StatusMarcacao> STATUS_QUE_OCUPAM_HORARIO =
            List.of(StatusMarcacao.AGUARDA_CONFIRMACAO, StatusMarcacao.CONFIRMADA);

    private final HorarioFuncionamentoRepository horarioRepository;
    private final DiaFechadoRepository diaFechadoRepository;
    private final MarcacaoRepository marcacaoRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;

    @Value("${app.marcacoes.atendimentos-simultaneos:1}")
    private int atendimentosSimultaneos;

    @Value("${app.marcacoes.intervalo-minutos:30}")
    private int intervaloMinutos;

    @Value("${app.marcacoes.dias-antecedencia-maxima:90}")
    private int diasAntecedenciaMaxima;

    /** Intervalo ocupado por uma marcação existente (e por quem, se tiver profissional). */
    private record Ocupacao(LocalTime inicio, LocalTime fim, UUID profissionalId) {
        boolean sobrepoe(LocalTime outroInicio, LocalTime outroFim) {
            return outroInicio.isBefore(fim) && inicio.isBefore(outroFim);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DisponibilidadeResponse disponibilidade(LocalDate data, List<UUID> servicosIds, UUID ignorarMarcacaoId, UUID profissionalId) {
        int duracao = duracaoDosServicos(servicosIds);

        String motivoFechado = motivoDiaIndisponivel(data);
        if (motivoFechado != null) {
            return new DisponibilidadeResponse(data, false, motivoFechado, duracao, List.of());
        }

        HorarioFuncionamento dia = horarioDoDia(data);
        List<Ocupacao> ocupacoes = ocupacoes(data, ignorarMarcacaoId);
        long capacidade = capacidade();
        LocalDateTime agora = LocalDateTime.now(FUSO_ESTUDIO);

        List<DisponibilidadeResponse.Horario> horarios = new ArrayList<>();
        for (LocalTime inicio = dia.getAbre(); cabeNoDia(dia, inicio, duracao); inicio = inicio.plusMinutes(intervaloMinutos)) {
            LocalTime fim = inicio.plusMinutes(duracao);
            // Horas que já passaram nem aparecem; as ocupadas aparecem riscadas.
            if (!sobrepoePausa(dia, inicio, fim) && LocalDateTime.of(data, inicio).isAfter(agora)) {
                horarios.add(new DisponibilidadeResponse.Horario(inicio, temVaga(ocupacoes, inicio, fim, capacidade, profissionalId)));
            }
            if (inicio.plusMinutes(intervaloMinutos).isBefore(inicio)) {
                break; // a próxima hora já seria no dia seguinte
            }
        }

        boolean algumLivre = horarios.stream().anyMatch(DisponibilidadeResponse.Horario::disponivel);
        return new DisponibilidadeResponse(data, true,
                algumLivre ? null : "Sem horários livres neste dia. Experimente outra data.", duracao, horarios);
    }

    @Override
    @Transactional(readOnly = true)
    public void validarMarcacao(LocalDate data, LocalTime hora, int duracaoMinutos, UUID ignorarMarcacaoId, UUID profissionalId) {
        String motivoFechado = motivoDiaIndisponivel(data);
        if (motivoFechado != null) {
            throw new RegraNegocioException(motivoFechado);
        }
        if (LocalDateTime.of(data, hora).isBefore(LocalDateTime.now(FUSO_ESTUDIO))) {
            throw new RegraNegocioException("Não é possível marcar para uma data ou hora que já passou.");
        }

        HorarioFuncionamento dia = horarioDoDia(data);
        LocalTime fim = hora.plusMinutes(duracaoMinutos);
        if (hora.isBefore(dia.getAbre()) || !cabeNoDia(dia, hora, duracaoMinutos)) {
            throw new RegraNegocioException("O estúdio funciona das " + dia.getAbre() + " às " + dia.getFecha()
                    + " neste dia e os serviços escolhidos demoram " + duracaoMinutos + " min. Escolha outra hora.");
        }
        if (sobrepoePausa(dia, hora, fim)) {
            throw new RegraNegocioException("Esse horário coincide com a pausa (" + dia.getPausaInicio()
                    + "–" + dia.getPausaFim() + "). Escolha outra hora.");
        }
        List<Ocupacao> ocupacoes = ocupacoes(data, ignorarMarcacaoId);
        if (!temVaga(ocupacoes, hora, fim, capacidade(), null)) {
            throw new RegraNegocioException("Este horário já está ocupado. Escolha outra hora.");
        }
        if (profissionalId != null && !temVaga(ocupacoes, hora, fim, capacidade(), profissionalId)) {
            throw new RegraNegocioException("A profissional escolhida já tem marcação a essa hora. Escolha outra hora ou outra profissional.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Profissional> escolherProfissional(LocalDate data, LocalTime hora, int duracaoMinutos, UUID ignorarMarcacaoId) {
        LocalTime fim = hora.plusMinutes(duracaoMinutos);
        Set<UUID> ocupadas = ocupacoes(data, ignorarMarcacaoId).stream()
                .filter(o -> o.profissionalId() != null && o.sobrepoe(hora, fim))
                .map(Ocupacao::profissionalId)
                .collect(Collectors.toSet());
        return profissionalRepository.findByAtivoTrueAndDeletadoFalseOrderByOrdemAscNomeAsc().stream()
                .filter(p -> !ocupadas.contains(p.getId()))
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public ConfiguracaoHorarioResponse configuracao() {
        List<HorarioDiaDto> semana = horarioRepository.findByDeletadoFalseOrderByDiaSemanaAsc().stream()
                .map(h -> new HorarioDiaDto(h.getDiaSemana(), h.isAberto(), h.getAbre(), h.getFecha(),
                        h.getPausaInicio(), h.getPausaFim()))
                .toList();
        List<ConfiguracaoHorarioResponse.DiaFechadoItem> fechados = diaFechadoRepository
                .findByDataGreaterThanEqualAndDeletadoFalseOrderByDataAsc(hoje()).stream()
                .map(d -> new ConfiguracaoHorarioResponse.DiaFechadoItem(d.getId(), d.getData(), d.getMotivo()))
                .toList();
        return new ConfiguracaoHorarioResponse(semana, fechados, intervaloMinutos, diasAntecedenciaMaxima);
    }

    @Override
    @Transactional
    public ConfiguracaoHorarioResponse atualizarHorario(AtualizarHorarioRequest request) {
        for (HorarioDiaDto dto : request.semana()) {
            validarDia(dto);
            HorarioFuncionamento dia = horarioRepository.findByDiaSemanaAndDeletadoFalse(dto.diaSemana())
                    .orElseGet(() -> {
                        HorarioFuncionamento novo = new HorarioFuncionamento();
                        novo.setDiaSemana(dto.diaSemana());
                        return novo;
                    });
            dia.setAberto(dto.aberto());
            dia.setAbre(dto.aberto() ? dto.abre() : null);
            dia.setFecha(dto.aberto() ? dto.fecha() : null);
            boolean temPausa = dto.aberto() && dto.pausaInicio() != null && dto.pausaFim() != null;
            dia.setPausaInicio(temPausa ? dto.pausaInicio() : null);
            dia.setPausaFim(temPausa ? dto.pausaFim() : null);
            horarioRepository.save(dia);
        }
        return configuracao();
    }

    @Override
    @Transactional
    public ConfiguracaoHorarioResponse adicionarDiaFechado(DiaFechadoRequest request) {
        if (request.data().isBefore(hoje())) {
            throw new RegraNegocioException("Escolha uma data de hoje em diante.");
        }
        if (diaFechadoRepository.findByDataAndDeletadoFalse(request.data()).isPresent()) {
            throw new RegraNegocioException("Esse dia já está marcado como fechado.");
        }
        DiaFechado dia = new DiaFechado();
        dia.setData(request.data());
        dia.setMotivo(request.motivo() == null || request.motivo().isBlank() ? null : request.motivo().trim());
        diaFechadoRepository.save(dia);
        return configuracao();
    }

    @Override
    @Transactional
    public ConfiguracaoHorarioResponse removerDiaFechado(UUID id) {
        DiaFechado dia = diaFechadoRepository.findById(id)
                .filter(d -> !d.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dia fechado não encontrado."));
        dia.marcarComoDeletado();
        diaFechadoRepository.save(dia);
        return configuracao();
    }

    /** Motivo pelo qual o dia inteiro não aceita marcações, ou null se está aberto. */
    private String motivoDiaIndisponivel(LocalDate data) {
        LocalDate hoje = hoje();
        if (data.isBefore(hoje)) {
            return "Essa data já passou.";
        }
        if (data.isAfter(hoje.plusDays(diasAntecedenciaMaxima))) {
            return "Aceitamos marcações até " + diasAntecedenciaMaxima + " dias de antecedência.";
        }
        var fechado = diaFechadoRepository.findByDataAndDeletadoFalse(data);
        if (fechado.isPresent()) {
            String motivo = fechado.get().getMotivo();
            return "O estúdio está fechado neste dia" + (motivo != null ? " (" + motivo + ")." : ".");
        }
        HorarioFuncionamento dia = horarioRepository.findByDiaSemanaAndDeletadoFalse(data.getDayOfWeek().getValue()).orElse(null);
        if (dia == null || !dia.isAberto() || dia.getAbre() == null || dia.getFecha() == null) {
            return "O estúdio não abre " + nomeDia(data.getDayOfWeek()) + ".";
        }
        return null;
    }

    private HorarioFuncionamento horarioDoDia(LocalDate data) {
        return horarioRepository.findByDiaSemanaAndDeletadoFalse(data.getDayOfWeek().getValue())
                .orElseThrow(() -> new RegraNegocioException("Horário de funcionamento não configurado."));
    }

    private static boolean cabeNoDia(HorarioFuncionamento dia, LocalTime inicio, int duracao) {
        LocalTime fim = inicio.plusMinutes(duracao);
        // fim antes do início = atravessou a meia-noite
        return !fim.isBefore(inicio) && !fim.isAfter(dia.getFecha());
    }

    private static boolean sobrepoePausa(HorarioFuncionamento dia, LocalTime inicio, LocalTime fim) {
        return dia.getPausaInicio() != null && dia.getPausaFim() != null
                && inicio.isBefore(dia.getPausaFim()) && dia.getPausaInicio().isBefore(fim);
    }

    /**
     * Há vaga se o estúdio ainda tem capacidade nesse intervalo e, quando se pede uma profissional,
     * se ela não tem outra marcação sobreposta. Marcações sem profissional ocupam uma vaga qualquer.
     */
    private static boolean temVaga(List<Ocupacao> ocupacoes, LocalTime inicio, LocalTime fim, long capacidade, UUID profissionalId) {
        List<Ocupacao> sobrepostas = ocupacoes.stream().filter(o -> o.sobrepoe(inicio, fim)).toList();
        if (sobrepostas.size() >= capacidade) {
            return false;
        }
        return profissionalId == null || sobrepostas.stream().noneMatch(o -> profissionalId.equals(o.profissionalId()));
    }

    /** Nº de profissionais ativas; sem equipa registada, vale ATENDIMENTOS_SIMULTANEOS. */
    private long capacidade() {
        long ativas = profissionalRepository.countByAtivoTrueAndDeletadoFalse();
        return ativas > 0 ? ativas : atendimentosSimultaneos;
    }

    private List<Ocupacao> ocupacoes(LocalDate data, UUID ignorarMarcacaoId) {
        return marcacaoRepository.buscarNaDataComServicos(data, STATUS_QUE_OCUPAM_HORARIO).stream()
                .filter(m -> !m.getId().equals(ignorarMarcacaoId))
                .map(m -> new Ocupacao(m.getHoraMarcacao(), m.getHoraMarcacao().plusMinutes(duracao(m)),
                        m.getProfissional() != null ? m.getProfissional().getId() : null))
                .toList();
    }

    private static int duracao(Marcacao marcacao) {
        return marcacao.getServicos().stream().mapToInt(i -> i.getServico().getDuracaoMinutos()).sum();
    }

    private int duracaoDosServicos(List<UUID> servicosIds) {
        if (servicosIds == null || servicosIds.isEmpty()) {
            return intervaloMinutos;
        }
        int duracao = servicoRepository.findAllById(servicosIds.stream().distinct().toList()).stream()
                .filter(s -> !s.isDeletado())
                .mapToInt(Servico::getDuracaoMinutos)
                .sum();
        return duracao > 0 ? duracao : intervaloMinutos;
    }

    private static void validarDia(HorarioDiaDto dto) {
        if (!dto.aberto()) {
            return;
        }
        String nome = "(" + DayOfWeek.of(dto.diaSemana()).getDisplayName(TextStyle.FULL, PORTUGUES) + ")";
        if (dto.abre() == null || dto.fecha() == null || !dto.abre().isBefore(dto.fecha())) {
            throw new RegraNegocioException("Horário inválido " + nome + ": a abertura tem de ser antes do fecho.");
        }
        boolean meiaPausa = (dto.pausaInicio() == null) != (dto.pausaFim() == null);
        if (meiaPausa) {
            throw new RegraNegocioException("Pausa incompleta " + nome + ": indique início e fim, ou nenhum.");
        }
        if (dto.pausaInicio() != null && (!dto.pausaInicio().isBefore(dto.pausaFim())
                || dto.pausaInicio().isBefore(dto.abre()) || dto.pausaFim().isAfter(dto.fecha()))) {
            throw new RegraNegocioException("Pausa inválida " + nome + ": tem de ficar dentro do horário de abertura.");
        }
    }

    private static String nomeDia(DayOfWeek dia) {
        String nome = dia.getDisplayName(TextStyle.FULL, PORTUGUES);
        return (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY ? "ao " : "à ") + nome;
    }

    private static LocalDate hoje() {
        return LocalDate.now(FUSO_ESTUDIO);
    }
}
