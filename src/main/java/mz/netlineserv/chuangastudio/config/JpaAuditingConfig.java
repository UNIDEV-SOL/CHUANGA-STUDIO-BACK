package mz.netlineserv.chuangastudio.config;

import mz.netlineserv.chuangastudio.security.UsuarioAutenticado;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditor")
public class JpaAuditingConfig {

    /**
     * Preenche criado_por / atualizado_por com o email de quem está autenticado.
     * Pedidos públicos (reserva sem conta, cotação) ficam "publico"; tarefas internas ficam "sistema".
     */
    @Bean
    public AuditorAware<String> auditor() {
        return () -> {
            Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
            if (autenticacao == null) {
                return Optional.of("sistema");
            }
            if (autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario) {
                return Optional.of(usuario.getUsername());
            }
            return Optional.of("publico");
        };
    }
}
