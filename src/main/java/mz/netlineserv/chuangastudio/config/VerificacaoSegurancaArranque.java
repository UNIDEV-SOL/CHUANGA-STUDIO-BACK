package mz.netlineserv.chuangastudio.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Recusa arrancar fora do perfil dev com os segredos de exemplo do repositório: com o JWT_SECRET
 * público qualquer pessoa forjaria um token de administrador. Em dev só avisa.
 */
@Slf4j
@Component
public class VerificacaoSegurancaArranque implements InitializingBean {

    static final String SEGREDO_EXEMPLO = "troque-esta-chave-em-producao-troque-esta-chave-em-producao";
    static final String SENHA_ADMIN_EXEMPLO = "ChuangaAdmin123";

    private final Environment ambiente;

    @Value("${jwt.secret}")
    private String segredoJwt;

    @Value("${app.admin.seed-password}")
    private String senhaAdmin;

    @Value("${app.cors.allowed-origins}")
    private String origens;

    @Value("${app.erros.detalhados:false}")
    private boolean errosDetalhados;

    public VerificacaoSegurancaArranque(Environment ambiente) {
        this.ambiente = ambiente;
    }

    @Override
    public void afterPropertiesSet() {
        boolean dev = ambiente.acceptsProfiles(Profiles.of("dev", "test"));
        List<String> problemas = new ArrayList<>();
        if (segredoJwt.contains("troque-esta-chave") || segredoJwt.equals(SEGREDO_EXEMPLO)) {
            problemas.add("JWT_SECRET é o valor de exemplo do repositório (gere um com: openssl rand -base64 48)");
        }
        if (SENHA_ADMIN_EXEMPLO.equals(senhaAdmin)) {
            problemas.add("ADMIN_SEED_PASSWORD é a palavra-passe de exemplo do repositório");
        }

        if (dev) {
            problemas.forEach(p -> log.warn("Segurança (só aceitável em desenvolvimento): {}", p));
            return;
        }
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("Arranque recusado por configuração insegura (perfis ativos: "
                    + String.join(",", ambiente.getActiveProfiles()) + "): " + String.join("; ", problemas) + ".");
        }
        if (origens.contains("localhost")) {
            log.warn("Segurança: CORS_ORIGINS inclui localhost fora do perfil dev ({}).", origens);
        }
        if (errosDetalhados) {
            log.warn("Segurança: ERROS_DETALHADOS=true fora do perfil dev; os erros 500 mostram mensagens internas ao cliente.");
        }
    }
}
