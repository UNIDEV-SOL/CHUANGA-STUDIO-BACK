package mz.netlineserv.chuangastudio.service.impl;

import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import mz.netlineserv.chuangastudio.domain.entity.TokenRecuperacaoSenha;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;
import mz.netlineserv.chuangastudio.dto.response.AutenticacaoResponse;
import mz.netlineserv.chuangastudio.notificacao.NotificacaoRecuperacaoSenhaEvent;
import mz.netlineserv.chuangastudio.repository.TokenRecuperacaoSenhaRepository;
import mz.netlineserv.chuangastudio.repository.UsuarioRepository;
import mz.netlineserv.chuangastudio.security.JwtService;
import mz.netlineserv.chuangastudio.security.ProtecaoLogin;
import mz.netlineserv.chuangastudio.service.MoradaResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Recuperação por OTP: email → código (com limite de tentativas) → nova palavra-passe → sessão iniciada. */
class AuthServiceImplRecuperacaoTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final TokenRecuperacaoSenhaRepository tokenRepository = mock(TokenRecuperacaoSenhaRepository.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthServiceImpl service;
    private Usuario ana;
    private TokenRecuperacaoSenha registo;

    @BeforeEach
    void preparar() {
        JwtService jwt = new JwtService();
        ReflectionTestUtils.setField(jwt, "segredo", "segredo-de-teste-com-mais-de-trinta-e-dois-bytes!!");
        ReflectionTestUtils.setField(jwt, "expiracaoMs", 60_000L);
        ReflectionTestUtils.invokeMethod(jwt, "iniciar");
        service = new AuthServiceImpl(usuarioRepository, encoder, jwt, tokenRepository, mock(MoradaResolver.class), eventos, new ProtecaoLogin(5, 15));
        ReflectionTestUtils.setField(service, "validadeRecuperacaoMinutos", 10);
        ReflectionTestUtils.setField(service, "maxTentativasCodigo", 3);

        ana = new Usuario();
        ReflectionTestUtils.setField(ana, "id", UUID.randomUUID());
        ana.setNome("Ana Silva");
        ana.setEmail("ana@exemplo.com");
        ana.setSenhaHash(encoder.encode("Antiga2025"));
        ana.setPapel(PapelUsuario.CLIENTE);
        when(usuarioRepository.findByEmailAndDeletadoFalse("ana@exemplo.com")).thenReturn(Optional.of(ana));
        when(tokenRepository.save(any())).thenAnswer(i -> {
            registo = i.getArgument(0);
            return registo;
        });
        when(tokenRepository.pedidoPendente("ana@exemplo.com")).thenAnswer(i ->
                Optional.ofNullable(registo).filter(r -> r.getUsadoEm() == null && r.getCodigoHash() != null));
        when(tokenRepository.buscarPorHash(anyString())).thenAnswer(i ->
                Optional.ofNullable(registo).filter(r -> r.getTokenHash().equals(i.getArgument(0))));
    }

    private String pedirCodigo() {
        service.solicitarRecuperacaoSenha("ana@exemplo.com");
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(evento.capture());
        return ((NotificacaoRecuperacaoSenhaEvent) evento.getValue()).codigo();
    }

    @Test
    void fluxoCompletoTerminaComSessaoIniciada() {
        String codigo = pedirCodigo();
        assertThat(codigo).matches("\\d{6}");
        assertThat(registo.getCodigoHash()).isNotEqualTo(codigo);

        String token = service.verificarCodigoRecuperacao("ana@exemplo.com", codigo);
        AutenticacaoResponse sessao = service.redefinirSenha(token, "Nova2026x", "Nova2026x");

        assertThat(sessao.getToken()).isNotBlank();
        assertThat(encoder.matches("Nova2026x", ana.getSenhaHash())).isTrue();
        assertThat(ana.getSenhaAlteradaEm()).isNotNull();
    }

    @Test
    void codigoErradoDizQuantasTentativasRestamEAnulaNoFim() {
        String codigo = pedirCodigo();
        String errado = codigo.equals("000000") ? "111111" : "000000";
        assertThatThrownBy(() -> service.verificarCodigoRecuperacao("ana@exemplo.com", errado)).hasMessage("Código incorreto. Restam 2 tentativas.");
        assertThatThrownBy(() -> service.verificarCodigoRecuperacao("ana@exemplo.com", errado)).hasMessage("Código incorreto. Resta 1 tentativa.");
        assertThatThrownBy(() -> service.verificarCodigoRecuperacao("ana@exemplo.com", errado)).hasMessageContaining("foi anulado");
        // Nem o código certo serve depois de anulado.
        assertThatThrownBy(() -> service.verificarCodigoRecuperacao("ana@exemplo.com", codigo)).hasMessageContaining("Não há nenhum código ativo");
    }

    @Test
    void codigoExpirado() {
        String codigo = pedirCodigo();
        registo.setExpiraEm(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> service.verificarCodigoRecuperacao("ana@exemplo.com", codigo)).hasMessageContaining("expirou");
    }

    @Test
    void codigoSoServeUmaVezEConfirmacaoTemDeCoincidir() {
        String codigo = pedirCodigo();
        String token = service.verificarCodigoRecuperacao("ana@exemplo.com", codigo);
        assertThatThrownBy(() -> service.verificarCodigoRecuperacao("ana@exemplo.com", codigo)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.redefinirSenha(token, "Nova2026x", "Outra2026x")).hasMessage("As palavras-passe não coincidem.");
        assertThatThrownBy(() -> service.redefinirSenha(token, "Antiga2025", "Antiga2025")).hasMessageContaining("diferente da anterior");
    }

    @Test
    void tokenInventadoNaoRedefine() {
        assertThatThrownBy(() -> service.redefinirSenha("inventado", "Nova2026x", "Nova2026x")).hasMessageContaining("Pedido de recuperação inválido");
    }
}
