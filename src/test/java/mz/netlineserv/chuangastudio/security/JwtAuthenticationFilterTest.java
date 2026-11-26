package mz.netlineserv.chuangastudio.security;

import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Cada motivo de recusa do token fica registado com o código certo. */
class JwtAuthenticationFilterTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-trinta-e-dois-bytes!!";

    private JwtService jwtService;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        jwtService = novoJwt(3_600_000);
        usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        usuario.setNome("Ana");
        usuario.setEmail("ana@exemplo.com");
        usuario.setSenhaHash("x");
        usuario.setPapel(PapelUsuario.CLIENTE);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private static JwtService novoJwt(long expiracaoMs) {
        JwtService s = new JwtService();
        ReflectionTestUtils.setField(s, "segredo", SEGREDO);
        ReflectionTestUtils.setField(s, "expiracaoMs", expiracaoMs);
        s.iniciar();
        return s;
    }

    private FalhaAutenticacao autenticar(String cabecalho) throws Exception {
        UserDetailsService uds = email -> {
            if (!email.equals(usuario.getEmail())) throw new UsernameNotFoundException(email);
            return new UsuarioAutenticado(usuario);
        };
        JwtAuthenticationFilter filtro = new JwtAuthenticationFilter(jwtService, uds);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/minha-area/perfil");
        req.addHeader("Authorization", cabecalho);
        filtro.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        return FalhaAutenticacao.doPedido(req);
    }

    @Test
    void tokenValidoAutentica() throws Exception {
        assertThat(autenticar("Bearer " + jwtService.gerarToken(new UsuarioAutenticado(usuario)))).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void tokenExpirado() throws Exception {
        String token = novoJwt(-1000).gerarToken(new UsuarioAutenticado(usuario));
        assertThat(autenticar("Bearer " + token)).isEqualTo(FalhaAutenticacao.TOKEN_EXPIRADO);
    }

    @Test
    void tokenAlterado() throws Exception {
        String token = jwtService.gerarToken(new UsuarioAutenticado(usuario));
        assertThat(autenticar("Bearer " + token.substring(0, token.length() - 3) + "abc")).isEqualTo(FalhaAutenticacao.TOKEN_INVALIDO);
        assertThat(autenticar("Bearer lixo")).isEqualTo(FalhaAutenticacao.TOKEN_INVALIDO);
        assertThat(autenticar("Token abc")).isEqualTo(FalhaAutenticacao.CABECALHO_INVALIDO);
    }

    @Test
    void contaDesativadaOuApagada() throws Exception {
        String token = jwtService.gerarToken(new UsuarioAutenticado(usuario));
        usuario.setContaAtiva(false);
        assertThat(autenticar("Bearer " + token)).isEqualTo(FalhaAutenticacao.CONTA_DESATIVADA);
        usuario.setContaAtiva(true);
        usuario.setEmail("outra@exemplo.com");
        assertThat(autenticar("Bearer " + token)).isEqualTo(FalhaAutenticacao.CONTA_INEXISTENTE);
    }

    @Test
    void trocarAPalavraPasseRevogaSessoesAnteriores() throws Exception {
        String antigo = jwtService.gerarToken(new UsuarioAutenticado(usuario));
        usuario.setSenhaAlteradaEm(LocalDateTime.now().plusSeconds(2));
        assertThat(autenticar("Bearer " + antigo)).isEqualTo(FalhaAutenticacao.SESSAO_REVOGADA);

        usuario.setSenhaAlteradaEm(LocalDateTime.now().minusSeconds(5));
        assertThat(autenticar("Bearer " + jwtService.gerarToken(new UsuarioAutenticado(usuario)))).isNull();
    }

    @Test
    void recusaSegredoCurto() {
        JwtService s = new JwtService();
        ReflectionTestUtils.setField(s, "segredo", "curto");
        org.assertj.core.api.Assertions.assertThatThrownBy(s::iniciar).hasMessageContaining("pelo menos 32");
    }
}
