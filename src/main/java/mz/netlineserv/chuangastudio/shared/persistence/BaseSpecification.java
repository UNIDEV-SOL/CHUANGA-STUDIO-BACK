package mz.netlineserv.chuangastudio.shared.persistence;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Construtor de filtros para os endpoints paginados (mesma ideia do SPO-BACK): cada método só
 * acrescenta a condição se o valor vier preenchido, por isso os parâmetros são todos opcionais.
 * Os caminhos aceitam pontos para relações ("cliente.nome").
 */
public class BaseSpecification<T> {

    private final List<Specification<T>> specifications = new ArrayList<>();

    public static <T> BaseSpecification<T> of(Class<T> tipo) {
        BaseSpecification<T> spec = new BaseSpecification<>();
        spec.specifications.add((root, query, cb) -> cb.isFalse(root.get("deletado")));
        return spec;
    }

    public BaseSpecification<T> equals(String campo, Object valor) {
        if (preenchido(valor)) {
            specifications.add((root, query, cb) -> cb.equal(caminho(root, campo), valor));
        }
        return this;
    }

    public BaseSpecification<T> in(String campo, Collection<?> valores) {
        if (valores != null && !valores.isEmpty()) {
            specifications.add((root, query, cb) -> caminho(root, campo).in(valores));
        }
        return this;
    }

    public BaseSpecification<T> like(String campo, String valor) {
        if (valor != null && !valor.isBlank()) {
            String padrao = "%" + valor.trim().toLowerCase() + "%";
            specifications.add((root, query, cb) -> cb.like(cb.lower(caminho(root, campo).as(String.class)), padrao));
        }
        return this;
    }

    /** Pesquisa livre: o texto aparece em pelo menos um dos campos. */
    public BaseSpecification<T> likeAny(String valor, String... campos) {
        if (valor != null && !valor.isBlank()) {
            String padrao = "%" + valor.trim().toLowerCase() + "%";
            specifications.add((root, query, cb) -> {
                List<Predicate> ou = new ArrayList<>();
                for (String campo : campos) {
                    ou.add(cb.like(cb.lower(caminho(root, campo).as(String.class)), padrao));
                }
                return cb.or(ou.toArray(Predicate[]::new));
            });
        }
        return this;
    }

    public BaseSpecification<T> between(String campo, LocalDate inicio, LocalDate fim) {
        if (inicio != null) {
            specifications.add((root, query, cb) -> cb.greaterThanOrEqualTo(caminho(root, campo).as(LocalDate.class), inicio));
        }
        if (fim != null) {
            specifications.add((root, query, cb) -> cb.lessThanOrEqualTo(caminho(root, campo).as(LocalDate.class), fim));
        }
        return this;
    }

    public BaseSpecification<T> isNull(String campo, Boolean aplicar) {
        if (Boolean.TRUE.equals(aplicar)) {
            specifications.add((root, query, cb) -> cb.isNull(caminho(root, campo)));
        }
        return this;
    }

    public BaseSpecification<T> and(Specification<T> spec) {
        if (spec != null) {
            specifications.add(spec);
        }
        return this;
    }

    public Specification<T> build() {
        return Specification.allOf(specifications);
    }

    private static <Y> Path<Y> caminho(jakarta.persistence.criteria.Root<?> root, String campo) {
        Path<?> path = root;
        for (String parte : campo.split("\\.")) {
            path = path.get(parte);
        }
        @SuppressWarnings("unchecked")
        Path<Y> resultado = (Path<Y>) path;
        return resultado;
    }

    private static boolean preenchido(Object valor) {
        return valor != null && !(valor instanceof String s && s.isBlank());
    }
}
