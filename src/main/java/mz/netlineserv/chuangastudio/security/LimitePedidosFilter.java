package mz.netlineserv.chuangastudio.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita pedidos POST abusivos aos endpoints abertos (login, registo, reservas, cotações)
 * por IP, numa janela deslizante em memória. Chega para uma única instância do backend;
 * com várias instâncias, passar para Redis ou para o proxy.
 */
@Slf4j
public class LimitePedidosFilter extends OncePerRequestFilter {

    private record Regra(String caminho, int maximo, Duration janela, String mensagem) {
    }

    private static final List<Regra> REGRAS = List.of(
            new Regra("/auth/entrar", 10, Duration.ofMinutes(5),
                    "Demasiadas tentativas de entrada. Espere alguns minutos e tente de novo."),
            new Regra("/auth/cadastrar", 5, Duration.ofMinutes(15),
                    "Demasiados registos a partir desta ligação. Tente mais tarde."),
            new Regra("/auth/recuperar-senha", 5, Duration.ofMinutes(15),
                    "Demasiados pedidos de recuperação. Verifique o seu email ou tente mais tarde."),
            new Regra("/auth/redefinir-senha", 10, Duration.ofMinutes(15),
                    "Demasiadas tentativas. Tente mais tarde."),
            new Regra("/publico/marcacoes", 10, Duration.ofMinutes(15),
                    "Demasiadas reservas a partir desta ligação. Tente mais tarde ou contacte-nos."),
            new Regra("/publico/cotacoes", 10, Duration.ofMinutes(15),
                    "Demasiados pedidos de cotação. Tente mais tarde ou contacte-nos.")
    );

    private final Map<String, Deque<Long>> pedidos = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final boolean ativo;

    public LimitePedidosFilter(ObjectMapper objectMapper, boolean ativo) {
        this.objectMapper = objectMapper;
        this.ativo = ativo;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        Regra regra = ativo && "POST".equals(request.getMethod()) ? regraPara(request.getServletPath()) : null;
        if (regra != null && !permitir(regra, ipDoCliente(request))) {
            log.warn("Limite de pedidos atingido em {} por {}", regra.caminho(), ipDoCliente(request));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(regra.janela().toSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), ApiResponse.erro(regra.mensagem()));
            return;
        }
        chain.doFilter(request, response);
    }

    private static Regra regraPara(String caminho) {
        return REGRAS.stream().filter(r -> r.caminho().equals(caminho)).findFirst().orElse(null);
    }

    private boolean permitir(Regra regra, String ip) {
        long agora = System.currentTimeMillis();
        long limite = agora - regra.janela().toMillis();
        Deque<Long> fila = pedidos.computeIfAbsent(regra.caminho() + "|" + ip, k -> new ArrayDeque<>());
        synchronized (fila) {
            while (!fila.isEmpty() && fila.peekFirst() < limite) {
                fila.pollFirst();
            }
            if (fila.size() >= regra.maximo()) {
                return false;
            }
            fila.addLast(agora);
        }
        // Limpeza ocasional para o mapa não crescer sem fim.
        if (pedidos.size() > 10_000) {
            pedidos.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || e.getValue().peekLast() < limite;
                }
            });
        }
        return true;
    }

    /** Atrás de um proxy (Render, Railway, Nginx) o IP real vem no X-Forwarded-For. */
    private static String ipDoCliente(HttpServletRequest request) {
        String encaminhado = request.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
