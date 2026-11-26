package mz.netlineserv.chuangastudio.modules.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.modules.quotations.repository.CotacaoRepository;
import mz.netlineserv.chuangastudio.modules.bookings.repository.MarcacaoRepository;
import mz.netlineserv.chuangastudio.modules.users.repository.UsuarioRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import mz.netlineserv.chuangastudio.modules.notifications.event.NotificacaoCotacaoEvent;
import mz.netlineserv.chuangastudio.modules.notifications.event.NotificacaoMarcacaoEvent;
import mz.netlineserv.chuangastudio.modules.notifications.event.NotificacaoRecuperacaoSenhaEvent;
import mz.netlineserv.chuangastudio.shared.email.EmailComposer;
import mz.netlineserv.chuangastudio.shared.email.EmailSender;

/**
 * Envia os emails (cliente e estúdio) depois do commit e fora da thread do pedido:
 * uma falha no Resend nunca desfaz nem atrasa a marcação/cotação.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoEmailListener {

    private final MarcacaoRepository marcacaoRepository;
    private final CotacaoRepository cotacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailComposer emailComposer;
    private final EmailSender emailSender;

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void aoNotificarMarcacao(NotificacaoMarcacaoEvent evento) {
        try {
            marcacaoRepository.buscarPorIdComDetalhes(evento.marcacaoId()).ifPresent(marcacao -> {
                if (temTexto(marcacao.getCliente().getEmail())) {
                    emailSender.enviar(emailComposer.comporMarcacao(marcacao, evento.tipo(), evento.anterior()));
                }
                if (evento.avisarEstudio()) {
                    emailComposer.comporAvisoEstudio(marcacao, evento.tipo(), evento.anterior()).ifPresent(emailSender::enviar);
                }
            });
        } catch (Exception ex) {
            log.error("Falha ao preparar o email {} da marcação {}", evento.tipo(), evento.marcacaoId(), ex);
        }
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void aoNotificarCotacao(NotificacaoCotacaoEvent evento) {
        try {
            cotacaoRepository.buscarPorIdComServicos(evento.cotacaoId()).ifPresent(cotacao -> {
                if (temTexto(cotacao.getEmail())) {
                    emailSender.enviar(emailComposer.comporCotacao(cotacao));
                }
                emailComposer.comporAvisoEstudio(cotacao).ifPresent(emailSender::enviar);
            });
        } catch (Exception ex) {
            log.error("Falha ao preparar o email da cotação {}", evento.cotacaoId(), ex);
        }
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void aoPedirRecuperacaoSenha(NotificacaoRecuperacaoSenhaEvent evento) {
        try {
            usuarioRepository.findById(evento.usuarioId()).ifPresent(usuario ->
                    emailSender.enviar(emailComposer.comporRecuperacaoSenha(usuario, evento.codigo(), evento.validadeMinutos())));
        } catch (Exception ex) {
            log.error("Falha ao enviar o email de recuperação de palavra-passe ao utilizador {}", evento.usuarioId(), ex);
        }
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
