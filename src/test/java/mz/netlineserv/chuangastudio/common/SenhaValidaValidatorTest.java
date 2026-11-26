package mz.netlineserv.chuangastudio.common;

import mz.netlineserv.chuangastudio.common.validacao.SenhaValidaValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SenhaValidaValidatorTest {

    @Test
    void explicaPorQueAPalavraPasseNaoServe() {
        assertThat(SenhaValidaValidator.erro("abc1")).contains("pelo menos 8").contains("tem 4");
        assertThat(SenhaValidaValidator.erro("abcdefgh")).contains("um número");
        assertThat(SenhaValidaValidator.erro("12345678")).contains("uma letra");
        assertThat(SenhaValidaValidator.erro(" abcdef12")).contains("espaços");
        assertThat(SenhaValidaValidator.erro("á".repeat(37) + "1")).contains("72 bytes");
        assertThat(SenhaValidaValidator.erro("Unhas2026")).isNull();
    }
}
