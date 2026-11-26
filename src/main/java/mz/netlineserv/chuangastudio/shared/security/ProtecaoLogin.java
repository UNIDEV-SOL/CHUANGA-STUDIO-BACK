package mz.netlineserv.chuangastudio.shared.security;

import mz.netlineserv.chuangastudio.shared.exceptions.LimiteExcedidoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueio temporário por conta (email) depois de várias palavras-passe erradas seguidas.
 * Completa o limite por IP: trava quem tenta a mesma conta a partir de muitos IPs.
 * Conta também emails que não existem, para a resposta não revelar quais existem.
 */
@Component
public class ProtecaoLogin {

    private record Estado(int falhas, long primeiraFalha, long bloqueadoAte) {
    }

    private final Map<String, Estado> estados = new ConcurrentHashMap<>();
    private final int maxFalhas;
    private final long janelaMs;
    private final long bloqueioMs;

    public ProtecaoLogin(@Value("${app.seguranca.login.max-falhas:5}") int maxFalhas,
                         @Value("${app.seguranca.login.bloqueio-minutos:15}") int bloqueioMinutos) {
        this.maxFalhas = maxFalhas;
        this.janelaMs = bloqueioMinutos * 60_000L;
        this.bloqueioMs = bloqueioMinutos * 60_000L;
    }

    /** Lança 429 se a conta está bloqueada neste momento. */
    public void verificar(String email) {
        Estado e = estados.get(chave(email));
        long agora = System.currentTimeMillis();
        if (e != null && e.bloqueadoAte() > agora) {
            long segundos = Math.max(1, (e.bloqueadoAte() - agora + 999) / 1000);
            throw new LimiteExcedidoException("CONTA_BLOQUEADA_TEMPORARIAMENTE",
                    "Esta conta ficou bloqueada depois de " + maxFalhas + " palavras-passe erradas seguidas. Tente de novo dentro de "
                            + LimitePedidosFilter.duracao(segundos) + " ou use \"Esqueci-me da palavra-passe\".", segundos);
        }
    }

    /** Regista uma falha; devolve quantas tentativas restam antes do bloqueio (0 = acabou de bloquear). */
    public int falhou(String email) {
        long agora = System.currentTimeMillis();
        Estado novo = estados.compute(chave(email), (k, e) -> {
            if (e == null || agora - e.primeiraFalha() > janelaMs || (e.bloqueadoAte() != 0 && e.bloqueadoAte() <= agora)) {
                e = new Estado(0, agora, 0);
            }
            int falhas = e.falhas() + 1;
            return new Estado(falhas, e.primeiraFalha(), falhas >= maxFalhas ? agora + bloqueioMs : 0);
        });
        if (estados.size() > 50_000) {
            estados.entrySet().removeIf(en -> agora - en.getValue().primeiraFalha() > janelaMs && en.getValue().bloqueadoAte() <= agora);
        }
        return Math.max(0, maxFalhas - novo.falhas());
    }

    public void sucesso(String email) {
        estados.remove(chave(email));
    }

    public int bloqueioMinutos() {
        return (int) (bloqueioMs / 60_000);
    }

    private static String chave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
