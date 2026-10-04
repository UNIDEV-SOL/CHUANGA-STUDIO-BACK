package mz.netlineserv.chuangastudio.notificacao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.domain.entity.Marcacao;
import mz.netlineserv.chuangastudio.domain.entity.MarcacaoServico;
import mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao;
import mz.netlineserv.chuangastudio.repository.MarcacaoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * "Está na hora da manutenção": X dias depois de um serviço com manutencao_dias,
 * se a cliente ainda não voltou nem tem marcação futura. Um só email por marcação.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManutencaoScheduler {

    private static final ZoneId FUSO_ESTUDIO = ZoneId.of("Africa/Maputo");
    /** Depois disto já não faz sentido lembrar (a cliente provavelmente foi a outro lado). */
    private static final int MAXIMO_DIAS = 90;

    private final MarcacaoRepository marcacaoRepository;
    private final EmailComposer emailComposer;
    private final EmailSender emailSender;

    @Value("${app.lembretes.ativo:true}")
    private boolean ativo;

    @Scheduled(cron = "${app.lembretes.manutencao-cron:0 0 10 * * *}", zone = "Africa/Maputo")
    @Transactional
    public void enviarLembretesManutencao() {
        if (!ativo) {
            return;
        }
        LocalDate hoje = LocalDate.now(FUSO_ESTUDIO);
        LocalDateTime agora = LocalDateTime.now(FUSO_ESTUDIO);
        for (Marcacao m : marcacaoRepository.buscarSemLembreteManutencao(hoje.minusDays(MAXIMO_DIAS), hoje.minusDays(1))) {
            MarcacaoServico item = m.getServicos().stream()
                    .filter(s -> s.getServico() != null && s.getServico().getManutencaoDias() != null && s.getServico().getManutencaoDias() > 0)
                    .min(Comparator.comparing(s -> s.getServico().getManutencaoDias()))
                    .orElse(null);
            if (item == null) {
                m.setLembreteManutencaoEm(agora); // nada a lembrar: não volta a ser candidata
                continue;
            }
            if (m.getDataMarcacao().plusDays(item.getServico().getManutencaoDias()).isAfter(hoje)) {
                continue; // ainda não chegou a altura
            }
            m.setLembreteManutencaoEm(agora);
            boolean jaVoltou = marcacaoRepository.clienteTemMarcacaoDepois(m.getCliente().getId(), m.getDataMarcacao(),
                    List.of(StatusMarcacao.AGUARDA_CONFIRMACAO, StatusMarcacao.CONFIRMADA, StatusMarcacao.CONCLUIDA));
            String email = m.getCliente().getEmail();
            if (jaVoltou || email == null || email.isBlank()) {
                continue;
            }
            try {
                emailSender.enviar(emailComposer.comporLembreteManutencao(m, item.getNomeServico(), item.getServico().getManutencaoDias()));
            } catch (Exception ex) {
                log.error("Falha no lembrete de manutenção da marcação {}", m.getId(), ex);
            }
        }
    }
}
