package mz.netlineserv.chuangastudio.notificacao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.domain.entity.Marcacao;
import mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao;
import mz.netlineserv.chuangastudio.repository.MarcacaoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Tarefas automáticas por email (gratuitas):
 *  - lembrete à cliente ~24 h antes de cada marcação confirmada;
 *  - agenda do dia seguinte para o estúdio, ao fim da tarde.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LembretesScheduler {

    private static final ZoneId FUSO_ESTUDIO = ZoneId.of("Africa/Maputo");

    private final MarcacaoRepository marcacaoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailComposer emailComposer;
    private final EmailSender emailSender;

    @Value("${app.lembretes.ativo:true}")
    private boolean ativo;

    @Value("${app.lembretes.horas-antes:24}")
    private int horasAntes;

    /** Corre de 15 em 15 minutos; cada marcação recebe um só lembrete (lembrete_enviado_em). */
    @Scheduled(cron = "${app.lembretes.cron:0 */15 * * * *}", zone = "Africa/Maputo")
    @Transactional
    public void enviarLembretes() {
        if (!ativo) {
            return;
        }
        LocalDateTime agora = LocalDateTime.now(FUSO_ESTUDIO);
        LocalDateTime limite = agora.plusHours(horasAntes);
        // Marcações confirmadas em cima da hora (menos de 1 h) já não precisam de lembrete.
        LocalDateTime minimo = agora.plusHours(1);

        List<Marcacao> candidatas = marcacaoRepository.buscarSemLembreteEntre(agora.toLocalDate(), limite.toLocalDate());
        for (Marcacao marcacao : candidatas) {
            LocalDateTime inicio = LocalDateTime.of(marcacao.getDataMarcacao(), marcacao.getHoraMarcacao());
            if (inicio.isAfter(minimo) && !inicio.isAfter(limite)) {
                marcacao.setLembreteEnviadoEm(agora);
                eventPublisher.publishEvent(new NotificacaoMarcacaoEvent(marcacao.getId(), NotificacaoMarcacaoEvent.Tipo.LEMBRETE, false));
            }
        }
    }

    /** Agenda de amanhã para a equipa (só se EMAIL_ESTUDIO estiver definido). */
    @Scheduled(cron = "${app.lembretes.agenda-estudio-cron:0 0 18 * * *}", zone = "Africa/Maputo")
    @Transactional(readOnly = true)
    public void enviarAgendaDeAmanha() {
        if (!ativo) {
            return;
        }
        LocalDate amanha = LocalDate.now(FUSO_ESTUDIO).plusDays(1);
        List<Marcacao> marcacoes = marcacaoRepository.buscarAgendaDoDia(amanha,
                List.of(StatusMarcacao.AGUARDA_CONFIRMACAO, StatusMarcacao.CONFIRMADA));
        if (marcacoes.isEmpty()) {
            return; // dia sem marcações (ou fechado): não vale a pena um email
        }
        try {
            emailComposer.comporAgendaEstudio(amanha, marcacoes).ifPresent(emailSender::enviar);
        } catch (Exception ex) {
            log.error("Falha ao enviar a agenda de {} ao estúdio", amanha, ex);
        }
    }
}
