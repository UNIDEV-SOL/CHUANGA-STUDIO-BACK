package mz.netlineserv.chuangastudio.modules.auth.service;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.exceptions.ContaDesativadaException;
import mz.netlineserv.chuangastudio.shared.exceptions.RegraNegocioException;
import mz.netlineserv.chuangastudio.modules.auth.domain.TokenRecuperacaoSenha;
import mz.netlineserv.chuangastudio.modules.users.domain.Usuario;
import mz.netlineserv.chuangastudio.modules.users.domain.PapelUsuario;
import mz.netlineserv.chuangastudio.modules.auth.request.CadastroRequest;
import mz.netlineserv.chuangastudio.modules.auth.request.LoginRequest;
import mz.netlineserv.chuangastudio.modules.auth.vm.AutenticacaoResponse;
import mz.netlineserv.chuangastudio.modules.users.vm.UsuarioResponse;
import mz.netlineserv.chuangastudio.modules.notifications.event.NotificacaoRecuperacaoSenhaEvent;
import mz.netlineserv.chuangastudio.modules.auth.repository.TokenRecuperacaoSenhaRepository;
import mz.netlineserv.chuangastudio.modules.users.repository.UsuarioRepository;
import mz.netlineserv.chuangastudio.shared.security.JwtService;
import mz.netlineserv.chuangastudio.shared.security.ProtecaoLogin;
import mz.netlineserv.chuangastudio.shared.security.UsuarioAutenticado;
import mz.netlineserv.chuangastudio.modules.locations.service.MoradaResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
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
public class AuthService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenRecuperacaoSenhaRepository tokenRepository;
    private final MoradaResolver moradaResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final ProtecaoLogin protecaoLogin;

    /** Hash de referência para comparar quando o email não existe (o tempo de resposta não revela contas). */
    private volatile String hashFicticio;

    /** Depois de confirmar o código, tempo para escolher a nova palavra-passe. */
    private static final int MINUTOS_PARA_NOVA_SENHA = 15;

    @Value("${app.seguranca.recuperacao-senha-minutos:10}")
    private int validadeRecuperacaoMinutos;

    @Value("${app.seguranca.recuperacao-senha-tentativas:5}")
    private int maxTentativasCodigo;

    public AutenticacaoResponse login(LoginRequest request) {
        String email = request.getEmail().trim();
        protecaoLogin.verificar(email);

        Usuario usuario = usuarioRepository.findByEmailAndDeletadoFalse(email).orElse(null);
        boolean senhaCerta = usuario != null
                ? passwordEncoder.matches(request.getSenha(), usuario.getSenhaHash())
                : passwordEncoder.matches(request.getSenha(), hashFicticio());
        if (!senhaCerta) {
            int restantes = protecaoLogin.falhou(email);
            throw new BadCredentialsException(restantes == 0
                    ? "Email ou palavra-passe inválidos. A conta ficou bloqueada durante " + protecaoLogin.bloqueioMinutos() + " minutos."
                    : "Email ou palavra-passe inválidos. " + (restantes == 1 ? "Resta 1 tentativa" : "Restam " + restantes + " tentativas")
                            + " antes de um bloqueio de " + protecaoLogin.bloqueioMinutos() + " minutos.");
        }
        protecaoLogin.sucesso(email);
        // Só depois de a palavra-passe estar certa, para não revelar o estado de contas alheias.
        if (!usuario.isContaAtiva()) {
            throw new ContaDesativadaException("Esta conta está desativada. Contacte o estúdio para a reativar.");
        }

        String token = jwtService.gerarToken(new UsuarioAutenticado(usuario));
        return new AutenticacaoResponse(token, UsuarioResponse.deEntidade(usuario));
    }

    private String hashFicticio() {
        if (hashFicticio == null) {
            hashFicticio = passwordEncoder.encode("senha-ficticia-para-tempo-constante");
        }
        return hashFicticio;
    }

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
        eventPublisher.publishEvent(new mz.netlineserv.chuangastudio.modules.notifications.event.NovoClienteEvent(usuario.getId()));

        UsuarioAutenticado usuarioAutenticado = new UsuarioAutenticado(usuario);
        String token = jwtService.gerarToken(usuarioAutenticado);

        return new AutenticacaoResponse(token, UsuarioResponse.deEntidade(usuario));
    }

    /** Envia o código OTP por email se a conta existir; responde sempre igual para não revelar que emails estão registados. */
    @Transactional
    public void solicitarRecuperacaoSenha(String email) {
        usuarioRepository.findByEmailAndDeletadoFalse(email.trim()).ifPresent(usuario -> {
            LocalDateTime agora = LocalDateTime.now();
            // Um pedido novo anula os códigos anteriores: só o último email vale.
            tokenRepository.invalidarDoUsuario(usuario.getId(), agora);

            String codigo = String.format("%06d", ALEATORIO.nextInt(1_000_000));
            TokenRecuperacaoSenha registo = new TokenRecuperacaoSenha();
            registo.setUsuario(usuario);
            registo.setCodigoHash(hashCodigo(usuario, codigo));
            // Ainda sem token: fica um valor aleatório até o código ser confirmado.
            registo.setTokenHash(hash(tokenAleatorio()));
            registo.setExpiraEm(agora.plusMinutes(validadeRecuperacaoMinutos));
            tokenRepository.save(registo);

            eventPublisher.publishEvent(new NotificacaoRecuperacaoSenhaEvent(usuario.getId(), codigo, validadeRecuperacaoMinutos));
        });
    }

    // As tentativas erradas ficam gravadas mesmo quando a resposta é um erro.
    /** Confirma o código do email; devolve o token curto que autoriza escolher a nova palavra-passe. */
    @Transactional(noRollbackFor = RegraNegocioException.class)
    public String verificarCodigoRecuperacao(String email, String codigo) {
        TokenRecuperacaoSenha registo = tokenRepository.pedidoPendente(email.trim())
                .orElseThrow(() -> new RegraNegocioException(
                        "Não há nenhum código ativo para este email. Peça um novo código."));
        LocalDateTime agora = LocalDateTime.now();
        if (!registo.getExpiraEm().isAfter(agora)) {
            registo.setUsadoEm(agora);
            throw new RegraNegocioException("O código expirou (era válido durante " + validadeRecuperacaoMinutos
                    + " minutos). Peça um novo código.");
        }

        boolean certo = MessageDigest.isEqual(
                registo.getCodigoHash().getBytes(StandardCharsets.UTF_8),
                hashCodigo(registo.getUsuario(), codigo.trim()).getBytes(StandardCharsets.UTF_8));
        if (!certo) {
            registo.setTentativas(registo.getTentativas() + 1);
            int restantes = maxTentativasCodigo - registo.getTentativas();
            if (restantes <= 0) {
                registo.setUsadoEm(agora);
                throw new RegraNegocioException("Código incorreto. Esgotou as " + maxTentativasCodigo
                        + " tentativas e este código foi anulado: peça um novo código.");
            }
            throw new RegraNegocioException("Código incorreto. " + (restantes == 1 ? "Resta 1 tentativa." : "Restam " + restantes + " tentativas."));
        }

        // Código certo: deixa de servir e dá lugar a um token curto para escolher a nova palavra-passe.
        String token = tokenAleatorio();
        registo.setCodigoHash(null);
        registo.setTokenHash(hash(token));
        registo.setExpiraEm(agora.plusMinutes(MINUTOS_PARA_NOVA_SENHA));
        tokenRepository.save(registo);
        return token;
    }

    /** Grava a nova palavra-passe e inicia sessão. */
    @Transactional
    public AutenticacaoResponse redefinirSenha(String token, String novaSenha, String confirmacaoSenha) {
        if (!novaSenha.equals(confirmacaoSenha)) {
            throw new RegraNegocioException("As palavras-passe não coincidem.");
        }
        TokenRecuperacaoSenha registo = tokenRepository.buscarPorHash(hash(token.trim()))
                .filter(t -> t.getCodigoHash() == null)
                .orElseThrow(() -> new RegraNegocioException("Pedido de recuperação inválido. Comece de novo e peça um código."));
        if (registo.getUsadoEm() != null) {
            throw new RegraNegocioException("Esta recuperação já foi concluída ou foi substituída por um pedido mais recente. Peça um novo código.");
        }
        if (!registo.getExpiraEm().isAfter(LocalDateTime.now())) {
            throw new RegraNegocioException("Passaram mais de " + MINUTOS_PARA_NOVA_SENHA
                    + " minutos desde a confirmação do código. Peça um novo código.");
        }

        Usuario usuario = registo.getUsuario();
        if (!usuario.isContaAtiva()) {
            throw new ContaDesativadaException("Esta conta está desativada. Contacte o estúdio para a reativar.");
        }
        if (passwordEncoder.matches(novaSenha, usuario.getSenhaHash())) {
            throw new RegraNegocioException("A nova palavra-passe tem de ser diferente da anterior.");
        }
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        // Termina as sessões abertas (ex.: de quem tinha a palavra-passe antiga).
        usuario.setSenhaAlteradaEm(LocalDateTime.now());
        usuarioRepository.save(usuario);
        protecaoLogin.sucesso(usuario.getEmail());
        tokenRepository.invalidarDoUsuario(usuario.getId(), LocalDateTime.now());

        // Entra logo na plataforma com a palavra-passe nova.
        return new AutenticacaoResponse(jwtService.gerarToken(new UsuarioAutenticado(usuario)), UsuarioResponse.deEntidade(usuario));
    }

    private static String tokenAleatorio() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** O id da conta entra no hash: o mesmo código noutra conta dá outro hash. */
    private static String hashCodigo(Usuario usuario, String codigo) {
        return hash(usuario.getId() + ":" + codigo);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }
}
