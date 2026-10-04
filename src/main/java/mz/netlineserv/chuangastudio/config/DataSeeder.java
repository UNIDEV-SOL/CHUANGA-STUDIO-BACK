package mz.netlineserv.chuangastudio.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;
import mz.netlineserv.chuangastudio.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
/**
 * Garante que existe pelo menos um usuário admin ao subir a aplicação.
 * Equivalente ao "admin seed" que o frontend tinha no usersStore local.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.seed-email}")
    private String emailAdmin;

    @Value("${app.admin.seed-password}")
    private String senhaAdmin;

    @Override
    public void run(String... args) {
        if (usuarioRepository.existsByEmailAndDeletadoFalse(emailAdmin)) {
            return;
        }

        Usuario admin = new Usuario();
        admin.setNome("Administrador Chuanga Studio");
        admin.setEmail(emailAdmin);
        admin.setTelefone("+258000000000");
        admin.setSenhaHash(passwordEncoder.encode(senhaAdmin));
        admin.setPapel(PapelUsuario.ADMIN);
        usuarioRepository.save(admin);

        log.info("Usuário admin inicial criado com o email: {}", emailAdmin);
    }
}
