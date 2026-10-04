package mz.netlineserv.chuangastudio.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import mz.netlineserv.chuangastudio.common.PageResponse;
import mz.netlineserv.chuangastudio.repository.spec.BaseSpecification;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import mz.netlineserv.chuangastudio.common.exception.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;
import mz.netlineserv.chuangastudio.dto.response.UsuarioResponse;
import mz.netlineserv.chuangastudio.repository.UsuarioRepository;
import mz.netlineserv.chuangastudio.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Contas da equipa no painel e troca da própria palavra-passe (qualquer utilizador). */
@RestController
@RequiredArgsConstructor
public class UtilizadorController {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;

    public record NovoAdminRequest(
            @NotBlank(message = "Nome é obrigatório") @Size(max = 150) String nome,
            @NotBlank(message = "Email é obrigatório") @Email(message = "Email inválido") String email,
            @Size(max = 30) String telefone,
            @NotBlank(message = "Palavra-passe é obrigatória")
            @Size(min = 8, max = 64, message = "A palavra-passe deve ter entre 8 e 64 caracteres") String senha) {
    }

    public record AtivoRequest(boolean ativo) {
    }

    public record TrocarSenhaRequest(
            @NotBlank(message = "Indique a palavra-passe atual") String senhaAtual,
            @NotBlank(message = "Indique a nova palavra-passe")
            @Size(min = 6, max = 64, message = "A nova palavra-passe deve ter entre 6 e 64 caracteres") String novaSenha) {
    }

    @GetMapping("/admin/utilizadores")
    public ResponseEntity<ApiResponse<PageResponse<UsuarioResponse>>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @ParameterObject @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        var spec = BaseSpecification.of(Usuario.class)
                .equals("papel", PapelUsuario.ADMIN)
                .likeAny(busca, "nome", "email")
                .equals("contaAtiva", ativo)
                .build();
        return ResponseEntity.ok(ApiResponse.sucesso(PageResponse.from(usuarioRepository.findAll(spec, pageable).map(UsuarioResponse::deEntidade))));
    }

    @PostMapping("/admin/utilizadores")
    @Transactional
    public ResponseEntity<ApiResponse<UsuarioResponse>> criar(@Valid @RequestBody NovoAdminRequest request) {
        String email = request.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmailAndDeletadoFalse(email)) {
            throw new RegraNegocioException("Já existe uma conta com este email.");
        }
        Usuario admin = new Usuario();
        admin.setNome(request.nome().trim());
        admin.setEmail(email);
        admin.setTelefone(request.telefone() == null || request.telefone().isBlank() ? null : request.telefone().trim());
        admin.setSenhaHash(passwordEncoder.encode(request.senha()));
        admin.setPapel(PapelUsuario.ADMIN);
        return ResponseEntity.ok(ApiResponse.sucesso("Utilizador criado. Partilhe a palavra-passe com a pessoa por um canal seguro.",
                UsuarioResponse.deEntidade(usuarioRepository.save(admin))));
    }

    @PatchMapping("/admin/utilizadores/{id}/ativo")
    @Transactional
    public ResponseEntity<ApiResponse<UsuarioResponse>> ativar(@PathVariable UUID id, @RequestBody AtivoRequest request) {
        Usuario admin = usuarioRepository.findById(id)
                .filter(u -> !u.isDeletado() && u.getPapel() == PapelUsuario.ADMIN)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Utilizador não encontrado."));
        if (!request.ativo()) {
            if (admin.getId().equals(usuarioService.obterUsuarioOnline().getId())) {
                throw new RegraNegocioException("Não pode desativar a sua própria conta.");
            }
            if (admin.isContaAtiva() && usuarioRepository.countByPapelAndContaAtivaTrueAndDeletadoFalse(PapelUsuario.ADMIN) <= 1) {
                throw new RegraNegocioException("Tem de ficar pelo menos um administrador ativo.");
            }
        }
        admin.setContaAtiva(request.ativo());
        return ResponseEntity.ok(ApiResponse.sucesso(request.ativo() ? "Conta reativada." : "Conta desativada. Perdeu o acesso ao painel.",
                UsuarioResponse.deEntidade(usuarioRepository.save(admin))));
    }

    @PatchMapping("/minha-area/senha")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> trocarSenha(@Valid @RequestBody TrocarSenhaRequest request) {
        Usuario usuario = usuarioService.obterUsuarioOnline();
        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenhaHash())) {
            throw new RegraNegocioException("A palavra-passe atual não está correta.");
        }
        usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        usuarioRepository.save(usuario);
        return ResponseEntity.ok(ApiResponse.sucesso("Palavra-passe alterada.", null));
    }
}
