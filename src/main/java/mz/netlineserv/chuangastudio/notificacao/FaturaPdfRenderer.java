package mz.netlineserv.chuangastudio.notificacao;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.domain.entity.Fatura;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

/** PDF da fatura a partir de templates/fatura/fatura.html. */
@Component
@RequiredArgsConstructor
public class FaturaPdfRenderer {

    private final PdfGerador pdfGerador;
    private final FaturaVista faturaVista;

    public byte[] gerar(Fatura fatura) {
        Context ctx = pdfGerador.contexto();
        faturaVista.preencher(ctx, fatura);
        return pdfGerador.gerar("fatura/fatura", ctx);
    }

    public static String nomeFicheiro(Fatura fatura) {
        return "Fatura-" + fatura.getNumero().replaceAll("[^A-Za-z0-9]+", "-") + ".pdf";
    }
}
