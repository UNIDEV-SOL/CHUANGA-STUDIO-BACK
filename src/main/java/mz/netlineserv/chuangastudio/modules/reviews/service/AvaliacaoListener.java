package mz.netlineserv.chuangastudio.modules.reviews.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.modules.invoices.domain.StatusFatura;
import mz.netlineserv.chuangastudio.modules.notifications.event.NotificacaoFaturaEvent;
import mz.netlineserv.chuangastudio.modules.invoices.repository.FaturaRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Depois da fatura, pede à cliente que avalie o atendimento (uma vez por marcação). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvaliacaoListener {

    private final FaturaRepository faturaRepository;
    private final AvaliacaoService avaliacaoService;

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoEmitirFatura(NotificacaoFaturaEvent evento) {
        try {
            faturaRepository.buscarPorIdComDetalhes(evento.faturaId())
                    .filter(f -> f.getStatus() != StatusFatura.ANULADA)
                    .ifPresent(f -> avaliacaoService.pedirAvaliacao(f.getMarcacao()));
        } catch (Exception ex) {
            log.error("Falha ao pedir a avaliação da fatura {}", evento.faturaId(), ex);
        }
    }
}
