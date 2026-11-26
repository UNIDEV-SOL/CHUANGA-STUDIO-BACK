package mz.netlineserv.chuangastudio.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Limites por IP/conta: bloqueia ao passar o limite, diz quando tentar de novo e não se contorna com X-Forwarded-For. */
class LimitePedidosFilterTest {

    private final LimitePedidosFilter filtro = new LimitePedidosFilter(
            new RespostasSeguranca(new ObjectMapper().registerModule(new JavaTimeModule())), true);

    private MockHttpServletResponse pedir(String metodo, String caminho, String ip, String encaminhado) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, "/api" + caminho);
        req.setContextPath("/api");
        req.setServletPath(caminho);
        req.setRemoteAddr(ip);
        if (encaminhado != null) req.addHeader("X-Forwarded-For", encaminhado);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filtro.doFilter(req, resp, new MockFilterChain());
        return resp;
    }

    @Test
    void bloqueiaLoginDepoisDoLimiteComMotivoExato() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(pedir("POST", "/auth/entrar", "1.1.1.1", null).getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse bloqueado = pedir("POST", "/auth/entrar", "1.1.1.1", null);
        assertThat(bloqueado.getStatus()).isEqualTo(429);
        assertThat(bloqueado.getHeader("Retry-After")).isNotBlank();
        assertThat(bloqueado.getContentAsString())
                .contains("\"codigo\":\"LIMITE_PEDIDOS\"")
                .contains("\"regra\":\"login\"")
                .contains("Atingiu o limite de tentativas de entrada: 10 em 5 min");
    }

    @Test
    void falsificarXForwardedForNaoContornaOLimite() throws Exception {
        for (int i = 0; i < 10; i++) {
            pedir("POST", "/auth/entrar", "2.2.2.2", "9.9.9." + i);
        }
        assertThat(pedir("POST", "/auth/entrar", "2.2.2.2", "8.8.8.8").getStatus()).isEqualTo(429);
    }

    @Test
    void cadaIpTemOSeuLimite() throws Exception {
        for (int i = 0; i < 10; i++) {
            pedir("POST", "/auth/entrar", "3.3.3.3", null);
        }
        assertThat(pedir("POST", "/auth/entrar", "4.4.4.4", null).getStatus()).isEqualTo(200);
    }

    @Test
    void limitaConsultasDeValesParaTravarAdivinhacaoDeCodigos() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(pedir("GET", "/publico/vales/CHG-AAAA-" + i, "5.5.5.5", null).getStatus()).isEqualTo(200);
        }
        assertThat(pedir("GET", "/publico/vales/CHG-ZZZZ-ZZZZ", "5.5.5.5", null).getStatus()).isEqualTo(429);
    }

    @Test
    void leiturasNormaisNaoSaoBloqueadasCedo() throws Exception {
        for (int i = 0; i < 200; i++) {
            assertThat(pedir("GET", "/publico/servicos", "6.6.6.6", null).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void duracaoLegivel() {
        assertThat(LimitePedidosFilter.duracao(45)).isEqualTo("45 s");
        assertThat(LimitePedidosFilter.duracao(300)).isEqualTo("5 min");
        assertThat(LimitePedidosFilter.duracao(200)).isEqualTo("3 min 20 s");
    }
}
