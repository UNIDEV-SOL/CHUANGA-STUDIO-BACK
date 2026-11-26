package mz.netlineserv.chuangastudio.notificacao;

import java.util.UUID;

/** O código em claro só vive neste evento (e no email); na base de dados fica o hash. */
public record NotificacaoRecuperacaoSenhaEvent(UUID usuarioId, String codigo, int validadeMinutos) {
}
