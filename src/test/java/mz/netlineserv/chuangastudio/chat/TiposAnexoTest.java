package mz.netlineserv.chuangastudio.chat;

import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ficheiros que correriam scripts no domínio da API (HTML, SVG) não entram nem saem como tal. */
class TiposAnexoTest {

    @Test
    void recusaSvgEHtml() {
        assertThatThrownBy(() -> TiposAnexo.exigirImagem("image/svg+xml"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("image/svg+xml");
        assertThatThrownBy(() -> TiposAnexo.exigirFicheiro("text/html", "pagina.html"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("pagina.html").hasMessageContaining("text/html");
    }

    @Test
    void aceitaVozDoNavegadorComParametros() {
        assertThat(TiposAnexo.exigirAudio("audio/webm;codecs=opus")).isEqualTo("audio/webm");
        assertThat(TiposAnexo.exigirFicheiro("application/pdf", "fatura.pdf")).isEqualTo("application/pdf");
    }

    @Test
    void anexoAntigoComTipoPerigosoSaiComoBinarioParaDescarregar() {
        ChatAnexo antigo = new ChatAnexo();
        antigo.setContentType("text/html");
        antigo.setNome("x.html");
        antigo.setDados("<script>alert(1)</script>".getBytes());
        ResponseEntity<byte[]> r = TiposAnexo.servir(antigo, TiposAnexo.cachePrivado(Duration.ofMinutes(1)));
        assertThat(r.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
        assertThat(r.getHeaders().getFirst("Content-Security-Policy")).contains("sandbox");
    }

    @Test
    void imagemAbreNoNavegador() {
        ChatAnexo foto = new ChatAnexo();
        foto.setContentType("image/png");
        foto.setDados(new byte[]{1});
        ResponseEntity<byte[]> r = TiposAnexo.servir(foto, TiposAnexo.cachePrivado(Duration.ofMinutes(1)));
        assertThat(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline");
    }
}
