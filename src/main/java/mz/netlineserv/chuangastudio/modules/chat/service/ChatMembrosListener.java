package mz.netlineserv.chuangastudio.modules.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.modules.notifications.event.NotificacaoMarcacaoEvent;
import mz.netlineserv.chuangastudio.modules.notifications.event.NovoClienteEvent;
import mz.netlineserv.chuangastudio.modules.users.repository.UsuarioRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Novas clientes entram na comunidade; quem marca um serviço entra no grupo desse serviço. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMembrosListener {

    private final GrupoService grupoService;
    private final UsuarioRepository usuarioRepository;

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCriarCliente(NovoClienteEvent evento) {
        try {
            usuarioRepository.findById(evento.usuarioId()).ifPresent(grupoService::garantirComunidade);
        } catch (Exception ex) {
            log.error("Falha ao juntar a cliente {} à comunidade", evento.usuarioId(), ex);
        }
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoMarcar(NotificacaoMarcacaoEvent evento) {
        if (evento.tipo() == NotificacaoMarcacaoEvent.Tipo.CANCELADA || evento.tipo() == NotificacaoMarcacaoEvent.Tipo.LEMBRETE) {
            return;
        }
        try {
            grupoService.juntarAosGruposDaMarcacao(evento.marcacaoId());
        } catch (Exception ex) {
            log.error("Falha ao juntar a cliente aos grupos da marcação {}", evento.marcacaoId(), ex);
        }
    }
}
