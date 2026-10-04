package mz.netlineserv.chuangastudio.notificacao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** Contactos e dados de pagamento do estúdio, usados nos emails e no PDF da fatura. */
@Component
public class DadosEstudio {

    @Value("${app.estudio.site-url}")
    private String siteUrl;

    @Value("${app.estudio.api-url}")
    private String apiUrl;

    @Value("${app.estudio.telefone}")
    private String telefone;

    @Value("${app.estudio.whatsapp}")
    private String whatsapp;

    @Value("${app.estudio.localizacao}")
    private String localizacao;

    @Value("${app.estudio.nuit:}")
    private String nuit;

    @Value("${app.estudio.mpesa:}")
    private String mpesa;

    @Value("${app.estudio.emola:}")
    private String emola;

    public String urlSite() {
        return siteUrl.replaceAll("/+$", "");
    }

    /** Link público (sem login) do PDF da fatura. */
    public String urlPdfFatura(String tokenPublico) {
        return apiUrl.replaceAll("/+$", "") + "/publico/faturas/" + tokenPublico + "/pdf";
    }

    /** Variável "estudio" dos templates. Map.of não aceita nulos, por isso HashMap. */
    public Map<String, Object> paraTemplate() {
        String site = urlSite();
        Map<String, Object> estudio = new HashMap<>();
        estudio.put("siteUrl", site);
        estudio.put("reservarUrl", site + "/reservar");
        estudio.put("minhaAreaUrl", site + "/minha-area");
        estudio.put("telefone", telefone);
        estudio.put("whatsappUrl", Formatos.whatsappUrl(whatsapp));
        estudio.put("localizacao", localizacao);
        estudio.put("ano", String.valueOf(LocalDate.now().getYear()));
        estudio.put("nuit", vazioParaNulo(nuit));
        estudio.put("mpesa", vazioParaNulo(mpesa));
        estudio.put("emola", vazioParaNulo(emola));
        return estudio;
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
