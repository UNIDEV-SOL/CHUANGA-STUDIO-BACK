package mz.netlineserv.chuangastudio.service.impl;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;
import mz.netlineserv.chuangastudio.dto.request.CadastroRequest;
import mz.netlineserv.chuangastudio.dto.request.LoginRequest;
import mz.netlineserv.chuangastudio.dto.response.AutenticacaoResponse;
import mz.netlineserv.chuangastudio.dto.response.UsuarioResponse;
import mz.netlineserv.chuangastudio.repository.UsuarioRepository;
import mz.netlineserv.chuangastudio.security.JwtService;
import mz.netlineserv.chuangastudio.security.UsuarioAutenticado;
import mz.netlineserv.chuangastudio.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

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
        usuario = usuarioRepository.save(usuario);

        UsuarioAutenticado usuarioAutenticado = new UsuarioAutenticado(usuario);
        String token = jwtService.gerarToken(usuarioAutenticado);

        return new AutenticacaoResponse(token, UsuarioResponse.deEntidade(usuario));
    }
}
