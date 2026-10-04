package mz.netlineserv.chuangastudio.service.impl;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import mz.netlineserv.chuangastudio.domain.entity.TokenRecuperacaoSenha;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;
import mz.netlineserv.chuangastudio.dto.request.CadastroRequest;
import mz.netlineserv.chuangastudio.dto.request.LoginRequest;
import mz.netlineserv.chuangastudio.dto.response.AutenticacaoResponse;
import mz.netlineserv.chuangastudio.dto.response.UsuarioResponse;
import mz.netlineserv.chuangastudio.notificacao.NotificacaoRecuperacaoSenhaEvent;
import mz.netlineserv.chuangastudio.repository.TokenRecuperacaoSenhaRepository;
import mz.netlineserv.chuangastudio.repository.UsuarioRepository;
import mz.netlineserv.chuangastudio.security.JwtService;
import mz.netlineserv.chuangastudio.security.UsuarioAutenticado;
import mz.netlineserv.chuangastudio.service.AuthService;
import mz.netlineserv.chuangastudio.service.MoradaResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenRecuperacaoSenhaRepository tokenRepository;
    private final MoradaResolver moradaResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.seguranca.recuperacao-senha-minutos:60}")
    private int validadeRecuperacaoMinutos;

    @Override
    public AutenticacaoResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getSenha())
        );

        Usuario usuario = usuarioRepository.findByEmailAndDeletadoFalse(request.getEmail())
                .orElseThrow(() -> new RegraNegocioException("Email ou palavra-passe inválidos."));

        UsuarioAutenticado usuarioAutenticado = new UsuarioAutenticado(usuario);
        String token = jwtService.gerarToken(usuarioAutenticado);

        return new AutenticacaoResponse(token, UsuarioResponse.deEntidade(usuario));
    }

    @Override
    @Transactional
    public AutenticacaoResponse cadastrar(CadastroRequest request) {
        if (usuarioRepository.existsByEmailAndDeletadoFalse(request.getEmail())) {
            throw new RegraNegocioException("Já existe uma conta com este email.");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(request.getNome());
        usuario.setEmail(request.getEmail());
        usuario.setTelefone(request.getTelefone());
        usuario.setSenhaHash(passwordEncoder.encode(request.getSenha()));
        usuario.setPapel(PapelUsuario.CLIENTE);
        moradaResolver.aplicar(usuario, request.getProvinciaId(), request.getDistritoId());
        usuario = usuarioRepository.save(usuario);
        eventPublisher.publishEvent(new mz.netlineserv.chuangastudio.notificacao.NovoClienteEvent(usuario.getId()));

        UsuarioAutenticado usuarioAutenticado = new UsuarioAutenticado(usuario);
        String token = jwtService.gerarToken(usuarioAutenticado);

        return new AutenticacaoResponse(token, UsuarioResponse.deEntidade(usuario));
    }

    @Override
    @Transactional
    public void solicitarRecuperacaoSenha(String email) {
        usuarioRepository.findByEmailAndDeletadoFalse(email.trim()).ifPresent(usuario -> {
            LocalDateTime agora = LocalDateTime.now();
            tokenRepository.invalidarDoUsuario(usuario.getId(), agora);

            byte[] bytes = new byte[32];
            ALEATORIO.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            TokenRecuperacaoSenha registo = new TokenRecuperacaoSenha();
            registo.setUsuario(usuario);
            registo.setTokenHash(hash(token));
            registo.setExpiraEm(agora.plusMinutes(validadeRecuperacaoMinutos));
            tokenRepository.save(registo);

            eventPublisher.publishEvent(new NotificacaoRecuperacaoSenhaEvent(usuario.getId(), token, validadeRecuperacaoMinutos));
        });
    }

    @Override
    @Transactional
    public void redefinirSenha(String token, String novaSenha) {
        TokenRecuperacaoSenha registo = tokenRepository.buscarPorHash(hash(token.trim()))
                .filter(t -> t.getUsadoEm() == null && t.getExpiraEm().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new RegraNegocioException("Este link expirou ou já foi usado. Peça um novo."));

        Usuario usuario = registo.getUsuario();
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);
        tokenRepository.invalidarDoUsuario(usuario.getId(), LocalDateTime.now());
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }
}
