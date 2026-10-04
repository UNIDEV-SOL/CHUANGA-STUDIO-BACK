package mz.netlineserv.chuangastudio.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.repository.ProvinciaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Mesmo modelo do ProvinceAndDistrictInitializer do SPO: depois de a base de dados estar configurada
 * (tabelas criadas pelo Flyway), corre o script de províncias e distritos se ainda estiverem vazias.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvinciasDistritosSeeder implements CommandLineRunner {

    private static final String SCRIPT = "db/seed/provincias_distritos.sql";

    private final ProvinciaRepository provinciaRepository;
    private final DataSource dataSource;

    @Override
    public void run(String... args) {
        if (provinciaRepository.count() > 0) {
            return;
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(SCRIPT));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
        log.info("Províncias e distritos inseridos a partir de {}.", SCRIPT);
    }
}
