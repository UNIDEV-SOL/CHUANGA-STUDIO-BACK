package mz.netlineserv.chuangastudio.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limita pedidos por pessoa (conta, quando há sessão; senão IP) com uma janela deslizante
 * aproximada em memória (dois contadores por chave, memória constante). Todas as regras que
 * casam com o pedido contam: as específicas (login, uploads, links públicos...) e as gerais.
 * Chega para uma única instância do backend; com várias, passar para Redis ou para o proxy.
 *
 * O IP vem de {@code request.getRemoteAddr()}: com {@code server.forward-headers-strategy=native}
 * o Tomcat só aceita o X-Forwarded-For vindo de proxies internos, por isso o cliente não o falsifica.
 */
@Slf4j
public class LimitePedidosFilter extends OncePerRequestFilter {

    private static final Set<String> ESCRITA = Set.of("POST", "PUT", "PATCH", "DELETE");

    record Regra(String nome, Set<String> metodos, List<String> padroes, int maximo, Duration janela, String descricao) {
        Regra(String nome, Set<String> metodos, String padrao, int maximo, Duration janela, String descricao) {
            this(nome, metodos, List.of(padrao), maximo, janela, descricao);
        }
    }

    static final List<Regra> REGRAS = List.of(
            // Autenticação
            new Regra("login", Set.of("POST"), "/auth/entrar", 10, Duration.ofMinutes(5), "tentativas de entrada"),
            new Regra("registo", Set.of("POST"), "/auth/cadastrar", 5, Duration.ofMinutes(15), "registos de conta"),
            new Regra("recuperar-senha", Set.of("POST"), "/auth/recuperar-senha", 5, Duration.ofMinutes(15), "pedidos de recuperação de palavra-passe"),
            new Regra("verificar-codigo", Set.of("POST"), "/auth/recuperar-senha/verificar", 10, Duration.ofMinutes(15), "tentativas de confirmar o código"),
            new Regra("redefinir-senha", Set.of("POST"), "/auth/redefinir-senha", 10, Duration.ofMinutes(15), "tentativas de redefinir a palavra-passe"),
            new Regra("trocar-senha", Set.of("PATCH"), "/minha-area/senha", 5, Duration.ofMinutes(15), "tentativas de trocar a palavra-passe"),
            // Formulários públicos
            new Regra("reservas", Set.of("POST"), "/publico/marcacoes", 10, Duration.ofMinutes(15), "reservas"),
            new Regra("cotacoes", Set.of("POST"), "/publico/cotacoes", 10, Duration.ofMinutes(15), "pedidos de cotação"),
            new Regra("avaliacoes", Set.of("POST"), "/publico/avaliacoes/pedido/*", 10, Duration.ofMinutes(15), "avaliações enviadas"),
            // Links com código/token: travam tentativas de adivinhar códigos
            new Regra("vales", Set.of("GET"), "/publico/vales/*", 20, Duration.ofMinutes(15), "consultas de vales"),
            new Regra("links-publicos", Set.of("GET"),
                    List.of("/publico/faturas/*/pdf", "/publico/avaliacoes/pedido/*"), 30, Duration.ofMinutes(15), "aberturas de links de faturas/avaliações"),
            // Uploads e chat
            new Regra("uploads", Set.of("POST", "PUT"),
                    List.of("/chat/conversas/*/imagens", "/chat/conversas/*/ficheiros", "/chat/grupos/*/foto",
                            "/minha-area/galeria/sugestoes", "/admin/galeria", "/admin/galeria/*/imagem"),
                    30, Duration.ofMinutes(10), "envios de ficheiros"),
            new Regra("mensagens", Set.of("POST"),
                    List.of("/chat/conversas/*/mensagens", "/chat/mensagens/*/encaminhar"), 60, Duration.ofMinutes(1), "mensagens enviadas"),
            // Gerais
            new Regra("escrita", ESCRITA, "/**", 120, Duration.ofMinutes(1), "alterações (POST/PUT/PATCH/DELETE)"),
            new Regra("global", Set.of(), "/**", 600, Duration.ofMinutes(1), "pedidos")
    );

    private static final AntPathMatcher CAMINHOS = new AntPathMatcher();

    /** Contadores da janela atual e da anterior; a estimativa pondera a anterior pelo tempo que falta. */
    private static final class Janela {
        long inicio;
        int atual;
        int anterior;
    }

    private record Bloqueio(Regra regra, long segundos) {
    }

    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();
    private final AtomicInteger pedidosDesdeLimpeza = new AtomicInteger();
    private final RespostasSeguranca respostas;
    private final boolean ativo;

    public LimitePedidosFilter(RespostasSeguranca respostas, boolean ativo) {
        this.respostas = respostas;
        this.ativo = ativo;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (!ativo || "OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String caminho = request.getServletPath();
        String quem = quem(request);
        long agora = System.currentTimeMillis();

        for (Regra regra : REGRAS) {
            if (!aplica(regra, request.getMethod(), caminho)) continue;
            Bloqueio bloqueio = registar(regra, quem, agora);
            if (bloqueio != null) {
                recusar(request, response, bloqueio, quem);
                return;
            }
        }
        limparDeVezEmQuando(agora);
        chain.doFilter(request, response);
    }

    private static boolean aplica(Regra regra, String metodo, String caminho) {
        if (!regra.metodos().isEmpty() && !regra.metodos().contains(metodo)) return false;
        return regra.padroes().stream().anyMatch(p -> CAMINHOS.match(p, caminho));
    }

    /** Conta o pedido; devolve o bloqueio (com os segundos até haver vaga) se a regra já está no limite. */
    private Bloqueio registar(Regra regra, String quem, long agora) {
        long tamanho = regra.janela().toMillis();
        long inicioAtual = agora - agora % tamanho;
        Janela j = janelas.computeIfAbsent(regra.nome() + "|" + quem, k -> new Janela());
        synchronized (j) {
            if (j.inicio != inicioAtual) {
                j.anterior = inicioAtual - j.inicio == tamanho ? j.atual : 0;
                j.atual = 0;
                j.inicio = inicioAtual;
            }
            long decorrido = agora - inicioAtual;
            double pesoAnterior = 1 - (double) decorrido / tamanho;
            if (j.anterior * pesoAnterior + j.atual >= regra.maximo()) {
                return new Bloqueio(regra, segundosAteVaga(j, regra.maximo(), tamanho, decorrido));
            }
            j.atual++;
            return null;
        }
    }

    private static long segundosAteVaga(Janela j, int maximo, long tamanho, long decorrido) {
        long ms;
        if (j.atual >= maximo || j.anterior == 0) {
            // Só a próxima janela liberta (e a contagem atual passa a pesar como "anterior").
            double fracao = j.atual == 0 ? 0 : Math.max(0, 1 - (double) (maximo - 1) / j.atual);
            ms = (tamanho - decorrido) + (long) (fracao * tamanho);
        } else {
            // Basta que o peso da janela anterior desça o suficiente.
            double fracaoNecessaria = 1 - (double) (maximo - j.atual) / j.anterior;
            ms = Math.max(0, (long) (fracaoNecessaria * tamanho) - decorrido);
        }
        return Math.max(1, (ms + 999) / 1000);
    }

    private void recusar(HttpServletRequest request, HttpServletResponse response, Bloqueio b, String quem) throws IOException {
        Regra r = b.regra();
        log.warn("Limite de pedidos '{}' atingido em {} {} por {}", r.nome(), request.getMethod(), request.getServletPath(), quem);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("regra", r.nome());
        dados.put("limite", r.maximo());
        dados.put("janelaSegundos", r.janela().toSeconds());
        dados.put("tentarDeNovoEmSegundos", b.segundos());
        String mensagem = "Atingiu o limite de " + r.descricao() + ": " + r.maximo() + " em " + duracao(r.janela().toSeconds())
                + ". Tente de novo dentro de " + duracao(b.segundos()) + ".";
        response.setHeader("Retry-After", String.valueOf(b.segundos()));
        respostas.escrever(response, HttpStatus.TOO_MANY_REQUESTS, ApiResponse.erro("LIMITE_PEDIDOS", mensagem, dados));
    }

    static String duracao(long segundos) {
        if (segundos < 60) return segundos + " s";
        long min = segundos / 60;
        long s = segundos % 60;
        return s == 0 ? min + " min" : min + " min " + s + " s";
    }

    private static String quem(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u) {
            return "conta:" + u.getId();
        }
        return "ip:" + request.getRemoteAddr();
    }

    /** Remove contadores que já não pesam (mais de duas janelas sem pedidos), para o mapa não crescer sem fim. */
    private void limparDeVezEmQuando(long agora) {
        if (pedidosDesdeLimpeza.incrementAndGet() < 5_000 && janelas.size() < 20_000) return;
        pedidosDesdeLimpeza.set(0);
        Map<String, Duration> janelaPorRegra = new LinkedHashMap<>();
        REGRAS.forEach(r -> janelaPorRegra.put(r.nome(), r.janela()));
        janelas.entrySet().removeIf(e -> {
            Duration janela = janelaPorRegra.get(e.getKey().substring(0, e.getKey().indexOf('|')));
            synchronized (e.getValue()) {
                return janela == null || agora - e.getValue().inicio > 2 * janela.toMillis();
            }
        });
    }
}
