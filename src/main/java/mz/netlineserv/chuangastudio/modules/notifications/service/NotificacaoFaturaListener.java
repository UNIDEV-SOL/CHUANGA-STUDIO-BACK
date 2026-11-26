package mz.netlineserv.chuangastudio.modules.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.modules.invoices.domain.Fatura;
import mz.netlineserv.chuangastudio.modules.users.domain.Usuario;
import mz.netlineserv.chuangastudio.modules.invoices.repository.FaturaRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import mz.netlineserv.chuangastudio.modules.invoices.pdf.FaturaPdfRenderer;
import mz.netlineserv.chuangastudio.modules.notifications.event.NotificacaoFaturaEvent;
import mz.netlineserv.chuangastudio.shared.email.EmailComposer;
import mz.netlineserv.chuangastudio.shared.email.EmailSender;
import mz.netlineserv.chuangastudio.shared.utils.Formatos;
import mz.netlineserv.chuangastudio.shared.whatsapp.WhatsAppSender;

/**
 * Envia a fatura em PDF ao cliente (email e WhatsApp) e uma cópia ao estúdio,
 * depois do commit e fora da thread do pedido, como os emails das marcações.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoFaturaListener {

    private final FaturaRepository faturaRepository;
    private final FaturaPdfRenderer faturaPdfRenderer;
    private final EmailComposer emailComposer;
    private final EmailSender emailSender;
    private final WhatsAppSender whatsAppSender;

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void aoNotificarFatura(NotificacaoFaturaEvent evento) {
        try {
            faturaRepository.buscarPorIdComDetalhes(evento.faturaId()).ifPresent(f -> enviar(f, evento));
        } catch (Exception ex) {
            log.error("Falha ao enviar a fatura {}", evento.faturaId(), ex);
        }
    }

    private void enviar(Fatura fatura, NotificacaoFaturaEvent evento) {
        byte[] pdf = faturaPdfRenderer.gerar(fatura);
        Usuario cliente = fatura.getMarcacao().getCliente();

        String email = evento.emailDestino() != null ? evento.emailDestino() : cliente.getEmail();
        if (email != null && !email.isBlank()) {
            emailSender.enviar(emailComposer.comporFatura(fatura, pdf, email));
        }
        if (evento.avisarEstudio()) {
            emailComposer.comporAvisoEstudio(fatura, pdf).ifPresent(emailSender::enviar);
        }

        if (cliente.getTelefone() != null && !cliente.getTelefone().isBlank()) {
            whatsAppSender.enviarFatura(cliente.getTelefone(), Formatos.primeiroNome(cliente.getNome()),
                    fatura.getNumero(), Formatos.dinheiro(fatura.getTotalAPagar()),
                    pdf, FaturaPdfRenderer.nomeFicheiro(fatura));
        }
    }
}
