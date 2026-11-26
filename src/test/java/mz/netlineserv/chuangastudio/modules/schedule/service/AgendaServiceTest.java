package mz.netlineserv.chuangastudio.modules.schedule.service;

import mz.netlineserv.chuangastudio.shared.exceptions.RegraNegocioException;
import mz.netlineserv.chuangastudio.modules.schedule.domain.DiaFechado;
import mz.netlineserv.chuangastudio.modules.schedule.domain.HorarioFuncionamento;
import mz.netlineserv.chuangastudio.modules.bookings.domain.Marcacao;
import mz.netlineserv.chuangastudio.modules.bookings.domain.MarcacaoServico;
import mz.netlineserv.chuangastudio.modules.catalog.domain.Servico;
import mz.netlineserv.chuangastudio.modules.schedule.vm.DisponibilidadeResponse;
import mz.netlineserv.chuangastudio.modules.schedule.repository.DiaFechadoRepository;
import mz.netlineserv.chuangastudio.modules.schedule.repository.HorarioFuncionamentoRepository;
import mz.netlineserv.chuangastudio.modules.professionals.domain.Profissional;
import mz.netlineserv.chuangastudio.modules.bookings.repository.MarcacaoRepository;
import mz.netlineserv.chuangastudio.modules.professionals.repository.ProfissionalRepository;
import mz.netlineserv.chuangastudio.modules.catalog.repository.ServicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Horas livres e validação de marcações: horário, pausa, dias fechados e sobreposição. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaServiceTest {

    @Mock HorarioFuncionamentoRepository horarioRepository;
    @Mock DiaFechadoRepository diaFechadoRepository;
    @Mock MarcacaoRepository marcacaoRepository;
    @Mock ServicoRepository servicoRepository;
    @Mock ProfissionalRepository profissionalRepository;

    @InjectMocks AgendaService service;

    /** Uma quarta-feira daqui a 1–2 semanas: no futuro e dentro da antecedência máxima. */
    private final LocalDate quarta = LocalDate.now(ZoneId.of("Africa/Maputo")).plusWeeks(1)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY));
    private Servico pedicure;

    @BeforeEach
    void preparar() {
        ReflectionTestUtils.setField(service, "atendimentosSimultaneos", 1);
        ReflectionTestUtils.setField(service, "intervaloMinutos", 30);
        ReflectionTestUtils.setField(service, "diasAntecedenciaMaxima", 90);

        // Quarta: 08:00–12:00 com pausa 10:00–10:30. Domingo fechado.
        when(horarioRepository.findByDiaSemanaAndDeletadoFalse(anyInt())).thenAnswer(inv -> {
            int dia = inv.getArgument(0);
            HorarioFuncionamento h = new HorarioFuncionamento();
            h.setDiaSemana(dia);
            h.setAberto(dia != 7);
            if (dia != 7) {
                h.setAbre(LocalTime.of(8, 0));
                h.setFecha(LocalTime.of(12, 0));
                h.setPausaInicio(LocalTime.of(10, 0));
                h.setPausaFim(LocalTime.of(10, 30));
            }
            return Optional.of(h);
        });
        when(diaFechadoRepository.findByDataAndDeletadoFalse(any())).thenReturn(Optional.empty());
        when(marcacaoRepository.buscarNaDataComServicos(any(), anyCollection())).thenReturn(List.of());

        pedicure = new Servico();
        pedicure.setId(UUID.randomUUID());
        pedicure.setDuracaoMinutos(60);
        when(servicoRepository.findAllById(List.of(pedicure.getId()))).thenReturn(List.of(pedicure));
    }

    @Test
    void horasRespeitamHorarioEPausa() {
        DisponibilidadeResponse d = service.disponibilidade(quarta, List.of(pedicure.getId()), null, null);

        // 60 min: 08:00, 08:30, 09:00 acabam antes da pausa; 10:30 e 11:00 depois. 09:30 e 10:00 caem na pausa.
        assertThat(d.aberto()).isTrue();
        assertThat(d.horarios()).extracting(DisponibilidadeResponse.Horario::hora).containsExactly(
                LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(10, 30), LocalTime.of(11, 0));
        assertThat(d.horarios()).allMatch(DisponibilidadeResponse.Horario::disponivel);
    }

    @Test
    void marcacaoExistenteOcupaAsHorasQueSeSobrepoem() {
        when(marcacaoRepository.buscarNaDataComServicos(eq(quarta), anyCollection()))
                .thenReturn(List.of(marcacao(LocalTime.of(8, 30), 60)));

        DisponibilidadeResponse d = service.disponibilidade(quarta, List.of(pedicure.getId()), null, null);

        // Ocupado 08:30–09:30: 08:00, 08:30 e 09:00 sobrepõem-se.
        assertThat(d.horarios()).filteredOn(h -> !h.disponivel()).extracting(DisponibilidadeResponse.Horario::hora)
                .containsExactly(LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0));
    }

    @Test
    void aoRemarcarAPropriaMarcacaoNaoConta() {
        Marcacao propria = marcacao(LocalTime.of(8, 30), 60);
        when(marcacaoRepository.buscarNaDataComServicos(eq(quarta), anyCollection())).thenReturn(List.of(propria));

        assertThatCode(() -> service.validarMarcacao(quarta, LocalTime.of(9, 0), 60, propria.getId(), null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validarMarcacao(quarta, LocalTime.of(9, 0), 60, null, null))
                .hasMessageContaining("ocupado");
    }

    @Test
    void domingoEstaFechado() {
        LocalDate domingo = quarta.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));

        DisponibilidadeResponse d = service.disponibilidade(domingo, List.of(), null, null);

        assertThat(d.aberto()).isFalse();
        assertThat(d.motivo()).contains("domingo");
        assertThatThrownBy(() -> service.validarMarcacao(domingo, LocalTime.of(9, 0), 60, null, null))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void diaFechadoMostraOMotivo() {
        DiaFechado feriado = new DiaFechado();
        feriado.setData(quarta);
        feriado.setMotivo("Feriado");
        when(diaFechadoRepository.findByDataAndDeletadoFalse(quarta)).thenReturn(Optional.of(feriado));

        DisponibilidadeResponse d = service.disponibilidade(quarta, List.of(), null, null);

        assertThat(d.aberto()).isFalse();
        assertThat(d.motivo()).contains("Feriado");
    }

    @Test
    void recusaQuandoNaoCabeAntesDoFecho() {
        assertThatThrownBy(() -> service.validarMarcacao(quarta, LocalTime.of(11, 30), 60, null, null))
                .hasMessageContaining("funciona");
    }

    @Test
    void recusaQuandoApanhaAPausa() {
        assertThatThrownBy(() -> service.validarMarcacao(quarta, LocalTime.of(9, 30), 60, null, null))
                .hasMessageContaining("pausa");
    }

    @Test
    void recusaDatasPassadasOuDemasiadoLonge() {
        LocalDate ontem = LocalDate.now(ZoneId.of("Africa/Maputo")).minusDays(1);
        assertThat(service.disponibilidade(ontem, List.of(), null, null).aberto()).isFalse();
        assertThat(service.disponibilidade(quarta.plusDays(120), List.of(), null, null).aberto()).isFalse();
    }

    @Test
    void comDuasProfissionaisCabemDuasMarcacoesAMesmaHora() {
        Profissional ana = profissional("Ana"), bia = profissional("Bia");
        when(profissionalRepository.countByAtivoTrueAndDeletadoFalse()).thenReturn(2L);
        when(profissionalRepository.findByAtivoTrueAndDeletadoFalseOrderByOrdemAscNomeAsc()).thenReturn(List.of(ana, bia));
        Marcacao daAna = marcacao(LocalTime.of(9, 0), 60);
        daAna.setProfissional(ana);
        when(marcacaoRepository.buscarNaDataComServicos(eq(quarta), anyCollection())).thenReturn(List.of(daAna));

        // Ainda há vaga às 9:00 (a Bia está livre), mas não com a Ana.
        assertThatCode(() -> service.validarMarcacao(quarta, LocalTime.of(9, 0), 60, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> service.validarMarcacao(quarta, LocalTime.of(9, 0), 60, null, bia.getId())).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validarMarcacao(quarta, LocalTime.of(9, 0), 60, null, ana.getId()))
                .hasMessageContaining("profissional escolhida");
        // A escolha automática dá a primeira livre.
        assertThat(service.escolherProfissional(quarta, LocalTime.of(9, 0), 60, null)).contains(bia);
    }

    @Test
    void comAsDuasOcupadasNaoHaVaga() {
        Profissional ana = profissional("Ana"), bia = profissional("Bia");
        when(profissionalRepository.countByAtivoTrueAndDeletadoFalse()).thenReturn(2L);
        Marcacao m1 = marcacao(LocalTime.of(9, 0), 60);
        m1.setProfissional(ana);
        Marcacao m2 = marcacao(LocalTime.of(8, 30), 90); // marcação antiga, sem profissional: ocupa uma vaga
        when(marcacaoRepository.buscarNaDataComServicos(eq(quarta), anyCollection())).thenReturn(List.of(m1, m2));

        assertThatThrownBy(() -> service.validarMarcacao(quarta, LocalTime.of(9, 0), 60, null, bia.getId()))
                .hasMessageContaining("ocupado");
    }

    private static Profissional profissional(String nome) {
        Profissional p = new Profissional();
        p.setId(UUID.randomUUID());
        p.setNome(nome);
        p.setAtivo(true);
        return p;
    }

    private Marcacao marcacao(LocalTime hora, int minutos) {
        Servico s = new Servico();
        s.setDuracaoMinutos(minutos);
        MarcacaoServico item = new MarcacaoServico();
        item.setServico(s);
        Marcacao m = new Marcacao();
        m.setId(UUID.randomUUID());
        m.setDataMarcacao(quarta);
        m.setHoraMarcacao(hora);
        m.adicionarServico(item);
        return m;
    }
}
