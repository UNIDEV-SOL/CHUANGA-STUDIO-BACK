package mz.netlineserv.chuangastudio.common.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class SenhaValidaValidator implements ConstraintValidator<SenhaValida, String> {

    public static final int MINIMO = 8;
    /** O BCrypt só usa os primeiros 72 bytes (e o Spring recusa mais do que isso). */
    public static final int MAXIMO_BYTES = 72;

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext ctx) {
        if (senha == null || senha.isEmpty()) {
            return true;
        }
        String erro = erro(senha);
        if (erro == null) {
            return true;
        }
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(erro).addConstraintViolation();
        return false;
    }

    /** Motivo exato pelo qual a palavra-passe não serve, ou null se serve. */
    public static String erro(String senha) {
        if (senha.length() < MINIMO) {
            return "A palavra-passe tem de ter pelo menos " + MINIMO + " caracteres (tem " + senha.length() + ").";
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
            return "A palavra-passe é demasiado longa: o máximo é " + MAXIMO_BYTES + " bytes (cerca de 72 caracteres sem acentos).";
        }
        if (senha.chars().noneMatch(Character::isLetter)) {
            return "A palavra-passe tem de ter pelo menos uma letra.";
        }
        if (senha.chars().noneMatch(Character::isDigit)) {
            return "A palavra-passe tem de ter pelo menos um número.";
        }
        if (!senha.strip().equals(senha)) {
            return "A palavra-passe não pode começar nem acabar com espaços.";
        }
        return null;
    }
}
