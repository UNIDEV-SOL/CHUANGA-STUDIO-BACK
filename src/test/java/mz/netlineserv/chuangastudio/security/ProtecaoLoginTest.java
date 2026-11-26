package mz.netlineserv.chuangastudio.security;

import mz.netlineserv.chuangastudio.common.exception.LimiteExcedidoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtecaoLoginTest {

    @Test
    void bloqueiaAContaDepoisDeFalhasSeguidas() {
        ProtecaoLogin p = new ProtecaoLogin(3, 15);
        assertThat(p.falhou("Ana@Exemplo.com")).isEqualTo(2);
        assertThat(p.falhou("ana@exemplo.com")).isEqualTo(1);
        assertThatCode(() -> p.verificar("ana@exemplo.com")).doesNotThrowAnyException();
        assertThat(p.falhou("ana@exemplo.com")).isZero();
        assertThatThrownBy(() -> p.verificar("ANA@exemplo.com"))
                .isInstanceOf(LimiteExcedidoException.class)
                .hasMessageContaining("3 palavras-passe erradas");
        assertThatCode(() -> p.verificar("outra@exemplo.com")).doesNotThrowAnyException();
    }

    @Test
    void sucessoLimpaAsFalhas() {
        ProtecaoLogin p = new ProtecaoLogin(3, 15);
        p.falhou("ana@exemplo.com");
        p.falhou("ana@exemplo.com");
        p.sucesso("ana@exemplo.com");
        assertThat(p.falhou("ana@exemplo.com")).isEqualTo(2);
    }
}
