package mz.netlineserv.chuangastudio.notificacao;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;

/**
 * Template Thymeleaf -> PDF com a identidade do estúdio (as fontes do site e o coração do logótipo).
 * Usado pela fatura e pelos relatórios de caixa.
 */
@Component
@RequiredArgsConstructor
public class PdfGerador {

    private record Fonte(String ficheiro, String familia, int peso, FontStyle estilo) {
    }

    private static final List<Fonte> FONTES = List.of(
            new Fonte("Manrope-Regular.ttf", "Manrope", 400, FontStyle.NORMAL),
            new Fonte("Manrope-SemiBold.ttf", "Manrope", 600, FontStyle.NORMAL),
            new Fonte("Manrope-Bold.ttf", "Manrope", 700, FontStyle.NORMAL),
            new Fonte("CormorantGaramond-Medium.ttf", "Cormorant Garamond", 500, FontStyle.NORMAL),
            new Fonte("CormorantGaramond-SemiBold.ttf", "Cormorant Garamond", 600, FontStyle.NORMAL),
            new Fonte("CormorantGaramond-MediumItalic.ttf", "Cormorant Garamond", 500, FontStyle.ITALIC)
    );

    private final ITemplateEngine templateEngine;
    private final DadosEstudio dadosEstudio;

    /** Contexto já com "estudio", "coracao" e "coracaoClaro"; o chamador junta as suas variáveis. */
    public Context contexto() {
        Context ctx = new Context(Formatos.PORTUGUES);
        ctx.setVariable("estudio", dadosEstudio.paraTemplate());
        ctx.setVariable("coracao", imagemDataUri("templates/fatura/coracao.png"));
        ctx.setVariable("coracaoClaro", imagemDataUri("templates/fatura/coracao-claro.png"));
        return ctx;
    }

    public byte[] gerar(String template, Context ctx) {
        String html = templateEngine.process(template, ctx);
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            for (Fonte fonte : FONTES) {
                byte[] bytes = ler("fonts/" + fonte.ficheiro());
                builder.useFont(() -> new ByteArrayInputStream(bytes), fonte.familia(), fonte.peso(), fonte.estilo(), true);
            }
            builder.withW3cDocument(new W3CDom().fromJsoup(Jsoup.parse(html)), "/");
            builder.toStream(saida);
            builder.run();
            return saida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Não foi possível gerar o PDF " + template, ex);
        }
    }

    private static String imagemDataUri(String caminho) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(ler(caminho));
    }

    private static byte[] ler(String caminho) {
        try (var entrada = new ClassPathResource(caminho).getInputStream()) {
            return entrada.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Recurso em falta: " + caminho, ex);
        }
    }
}
