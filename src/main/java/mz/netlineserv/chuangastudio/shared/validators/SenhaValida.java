package mz.netlineserv.chuangastudio.shared.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Palavra-passe nova: 8 a 72 bytes (o limite do BCrypt) com pelo menos uma letra e um número.
 * Vazia ou nula passa: junte {@code @NotBlank} quando for obrigatória.
 */
@Documented
@Constraint(validatedBy = SenhaValidaValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SenhaValida {

    String message() default "Palavra-passe inválida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
