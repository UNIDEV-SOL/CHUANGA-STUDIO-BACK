package mz.netlineserv.chuangastudio.modules.reports.pdf;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.modules.reports.vm.RelatorioCaixaResponse;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import mz.netlineserv.chuangastudio.shared.pdf.PdfGerador;

/** PDF do relatório de caixa a partir de templates/relatorio/caixa.html. */
@Component
@RequiredArgsConstructor
public class RelatorioPdfRenderer {

    private final PdfGerador pdfGerador;
    private final RelatorioVista relatorioVista;

    public byte[] gerar(RelatorioCaixaResponse relatorio, String titulo) {
        Context ctx = pdfGerador.contexto();
        relatorioVista.preencher(ctx, relatorio, titulo);
        return pdfGerador.gerar("relatorio/caixa", ctx);
    }

    public static String nomeFicheiro(RelatorioCaixaResponse relatorio) {
        return relatorio.inicio().equals(relatorio.fim())
                ? "Relatorio-caixa-" + relatorio.inicio() + ".pdf"
                : "Relatorio-caixa-" + relatorio.inicio() + "-a-" + relatorio.fim() + ".pdf";
    }
}
