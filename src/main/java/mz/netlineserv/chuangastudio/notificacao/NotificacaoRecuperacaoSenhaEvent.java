package mz.netlineserv.chuangastudio.notificacao;

import java.util.UUID;

/** O token em claro só vive neste evento (e no link do email); na base de dados fica o hash. */
public record NotificacaoRecuperacaoSenhaEvent(UUID usuarioId, String token, int validadeMinutos) {
}
