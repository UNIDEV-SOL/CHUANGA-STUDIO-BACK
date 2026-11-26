package mz.netlineserv.chuangastudio.modules.notifications.event;

import java.util.UUID;

/** Conta de cliente criada (registo ou reserva sem conta). */
public record NovoClienteEvent(UUID usuarioId) {
}
