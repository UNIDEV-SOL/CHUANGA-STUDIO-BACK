package mz.netlineserv.chuangastudio.modules.loyalty.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.shared.email.EmailComposer;
import mz.netlineserv.chuangastudio.shared.email.EmailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import mz.netlineserv.chuangastudio.modules.loyalty.repository.ValeRepository;

/** Envia o vale-oferta por email a quem o recebe (quando foi indicado um email). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValeEmailListener {

    private final ValeRepository valeRepository;
    private final EmailComposer emailComposer;
    private final EmailSender emailSender;

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void aoEmitirVale(ValeService.ValeEmitidoEvent evento) {
        try {
            valeRepository.findById(evento.valeId())
                    .filter(v -> v.getBeneficiarioEmail() != null && !v.getBeneficiarioEmail().isBlank())
                    .ifPresent(v -> emailSender.enviar(emailComposer.comporVale(v)));
        } catch (Exception ex) {
            log.error("Falha ao enviar o vale {}", evento.valeId(), ex);
        }
    }
}
