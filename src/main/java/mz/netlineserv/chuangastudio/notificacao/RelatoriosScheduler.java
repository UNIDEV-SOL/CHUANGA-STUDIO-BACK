package mz.netlineserv.chuangastudio.notificacao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.dto.response.RelatorioCaixaResponse;
import mz.netlineserv.chuangastudio.service.RelatorioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Relatórios de caixa enviados por email ao estúdio (EMAIL_ESTUDIO), com o PDF em anexo:
 *  - semanal: sábado às 20:00, com os últimos 7 dias (domingo a sábado);
 *  - fecho do dia: segunda a sexta às 20:00 (ao sábado já vai o semanal). Dias sem movimento não geram email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelatoriosScheduler {

    private static final ZoneId FUSO_ESTUDIO = ZoneId.of("Africa/Maputo");

    private final RelatorioService relatorioService;
    private final RelatorioPdfRenderer relatorioPdfRenderer;
    private final EmailComposer emailComposer;
    private final EmailSender emailSender;

    @Value("${app.relatorios.semanal-ativo:true}")
    private boolean semanalAtivo;

    @Value("${app.relatorios.diario-ativo:true}")
    private boolean diarioAtivo;

    @Scheduled(cron = "${app.relatorios.semanal-cron:0 0 20 * * SAT}", zone = "Africa/Maputo")
    public void enviarSemanal() {
        if (!semanalAtivo) {
            return;
        }
        LocalDate hoje = LocalDate.now(FUSO_ESTUDIO);
        enviar(hoje.minusDays(6), hoje, "Relatório semanal", true);
    }

    @Scheduled(cron = "${app.relatorios.diario-cron:0 0 20 * * MON-FRI}", zone = "Africa/Maputo")
    public void enviarFechoDoDia() {
        if (!diarioAtivo) {
            return;
        }
        LocalDate hoje = LocalDate.now(FUSO_ESTUDIO);
        enviar(hoje, hoje, "Fecho do dia", false);
    }

    private void enviar(LocalDate inicio, LocalDate fim, String titulo, boolean mesmoSemMovimento) {
        try {
            RelatorioCaixaResponse relatorio = relatorioService.caixa(inicio, fim);
            boolean semMovimento = relatorio.atendimentos() == 0 && relatorio.recebido().signum() == 0;
            if (semMovimento && !mesmoSemMovimento) {
                log.info("{} de {}: sem movimento, email não enviado.", titulo, inicio);
                return;
            }
            byte[] pdf = relatorioPdfRenderer.gerar(relatorio, titulo);
            emailComposer.comporRelatorioEstudio(relatorio, titulo, pdf).ifPresent(emailSender::enviar);
        } catch (Exception ex) {
            log.error("Falha ao enviar o {} ({} a {})", titulo, inicio, fim, ex);
        }
    }
}
