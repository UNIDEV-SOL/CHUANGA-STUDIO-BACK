package mz.netlineserv.chuangastudio.notificacao;

import mz.netlineserv.chuangastudio.domain.entity.Fatura;
import mz.netlineserv.chuangastudio.domain.entity.FaturaItem;
import mz.netlineserv.chuangastudio.domain.entity.Marcacao;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.StatusFatura;
import mz.netlineserv.chuangastudio.dto.response.RelatorioCaixaResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gera os PDFs a sério nos piores casos habituais e verifica a paginação:
 * o relatório de uma semana e a fatura de um atendimento grande cabem numa página,
 * e nenhum PDF acaba com uma página quase vazia (só uma linha).
 */

class PdfPaginacaoTest {

    private PdfGerador pdfGerador;
    private RelatorioPdfRenderer relatorioRenderer;
    private FaturaPdfRenderer faturaRenderer;

    @BeforeEach
    void preparar() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        DadosEstudio estudio = new DadosEstudio();
        ReflectionTestUtils.setField(estudio, "siteUrl", "https://chuanga-studio-front.vercel.app");
        ReflectionTestUtils.setField(estudio, "apiUrl", "https://api.exemplo.co.mz/api");
        ReflectionTestUtils.setField(estudio, "telefone", "+258 84 658 9542");
        ReflectionTestUtils.setField(estudio, "whatsapp", "258846589542");
        ReflectionTestUtils.setField(estudio, "localizacao", "Maputo – Moçambique");
        ReflectionTestUtils.setField(estudio, "nuit", "400123456");
        ReflectionTestUtils.setField(estudio, "mpesa", "84 658 9542");
        ReflectionTestUtils.setField(estudio, "emola", "87 658 9542");

        pdfGerador = new PdfGerador(engine, estudio);
        relatorioRenderer = new RelatorioPdfRenderer(pdfGerador, new RelatorioVista());
        faturaRenderer = new FaturaPdfRenderer(pdfGerador, new FaturaVista(estudio));
    }

    @Test
    void relatorioSemanalCheioCabeNumaPagina() throws IOException {
        byte[] pdf = relatorioRenderer.gerar(relatorio(LocalDate.of(2026, 9, 27), 7), "Relatório semanal");
        assertThat(paginas(pdf)).isEqualTo(1);
    }

    @Test
    void relatorioDeUmMesAgrupaPorSemanaECabeNumaPagina() throws IOException {
        byte[] pdf = relatorioRenderer.gerar(relatorio(LocalDate.of(2026, 10, 1), 31), "Relatório do período");
        assertThat(paginas(pdf)).isEqualTo(1);
        assertThat(texto(pdf)).contains("Por semana").contains("Semana de");
    }

    @Test
    void relatorioDeUmAnoNaoDeixaPaginasComUmaSoLinha() throws IOException {
        byte[] pdf = relatorioRenderer.gerar(relatorio(LocalDate.of(2026, 1, 1), 365), "Relatório do período");
        assertSemPaginaQuaseVazia(pdf);
    }

    @Test
    void faturaComOitoServicosCabeNumaPagina() throws IOException {
        byte[] pdf = faturaRenderer.gerar(fatura(8));
        assertThat(paginas(pdf)).isEqualTo(1);
    }

    @Test
    void faturaMuitoGrandeNaoDeixaPaginasComUmaSoLinha() throws IOException {
        assertSemPaginaQuaseVazia(faturaRenderer.gerar(fatura(20)));
    }

    // --- dados de teste (valores e textos longos, de propósito) ---

    private static RelatorioCaixaResponse relatorio(LocalDate inicio, int dias) {
        List<RelatorioCaixaResponse.PorDia> porDia = new ArrayList<>();
        for (int i = 0; i < dias; i++) {
            porDia.add(new RelatorioCaixaResponse.PorDia(inicio.plusDays(i),
                    new BigDecimal(1500 + i * 37), new BigDecimal(900 + i * 11)));
        }
        List<RelatorioCaixaResponse.PorForma> formas = List.of(
                new RelatorioCaixaResponse.PorForma("MPESA", "M-Pesa", new BigDecimal("12500"), 14),
                new RelatorioCaixaResponse.PorForma("NUMERARIO", "Numerário", new BigDecimal("8200"), 9),
                new RelatorioCaixaResponse.PorForma("EMOLA", "e-Mola", new BigDecimal("4300"), 5),
                new RelatorioCaixaResponse.PorForma("CARTAO", "Cartão", new BigDecimal("3100"), 3),
                new RelatorioCaixaResponse.PorForma("TRANSFERENCIA", "Transferência bancária", new BigDecimal("2250"), 2),
                new RelatorioCaixaResponse.PorForma("SEM_PAGAMENTO", "Coberto pelo adiantamento", BigDecimal.ZERO, 1));
        List<RelatorioCaixaResponse.PorServico> servicos = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            servicos.add(new RelatorioCaixaResponse.PorServico("Alongamento em gel com decoração personalizada nº " + i,
                    11 - i, new BigDecimal(12000 - i * 900)));
        }
        return new RelatorioCaixaResponse(inicio, inicio.plusDays(dias - 1),
                new BigDecimal("98765"), new BigDecimal("30350"), new BigDecimal("1200"), new BigDecimal("4500"),
                new BigDecimal("3450"), 3, 42, new BigDecimal("2351.55"), 4,
                formas, porDia, servicos);
    }

    private static Fatura fatura(int itens) {
        Usuario cliente = new Usuario();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Maria José da Conceição Macuácua");
        cliente.setEmail("maria.jose.conceicao.macuacua@exemplo.co.mz");
        cliente.setTelefone("846589542");

        Marcacao marcacao = new Marcacao();
        marcacao.setId(UUID.randomUUID());
        marcacao.setCliente(cliente);
        marcacao.setDataMarcacao(LocalDate.of(2026, 10, 5));
        marcacao.setHoraMarcacao(LocalTime.of(9, 0));

        Fatura fatura = new Fatura();
        fatura.setMarcacao(marcacao);
        fatura.setNumero("FT 2026/0123");
        fatura.setStatus(StatusFatura.PENDENTE);
        fatura.setDataEmissao(LocalDateTime.of(2026, 10, 5, 12, 30));
        fatura.setTokenPublico("token-de-teste");
        fatura.setClienteNuit("123456789");
        fatura.setObservacoes("Cliente pediu efeito cat eye em duas unhas e decoração com pedrarias durante o atendimento. "
                + "Próxima manutenção recomendada daqui a três semanas.");
        BigDecimal subtotal = BigDecimal.ZERO;
        for (int i = 1; i <= itens; i++) {
            FaturaItem item = new FaturaItem();
            item.setDescricao("Serviço de manicure spa com hidratação profunda nº " + i);
            item.setQuantidade(i % 3 + 1);
            item.setPrecoUnitario(new BigDecimal("650.00"));
            item.setTotal(item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
            item.setCriadoEm(LocalDateTime.now());
            fatura.adicionarItem(item);
            subtotal = subtotal.add(item.getTotal());
        }
        fatura.setSubtotal(subtotal);
        fatura.setDesconto(new BigDecimal("100.00"));
        fatura.setAdiantamentoDeduzido(new BigDecimal("150.00"));
        fatura.setTotalAPagar(subtotal.subtract(new BigDecimal("250.00")));
        return fatura;
    }

    // --- verificações ---

    /** Com -Dpdf.debug=pasta, guarda os PDFs gerados para os ver. */
    private static void guardar(byte[] pdf) throws IOException {
        String pasta = System.getProperty("pdf.debug");
        if (pasta != null) {
            java.nio.file.Files.write(java.nio.file.Path.of(pasta, "teste-" + System.nanoTime() + ".pdf"), pdf);
        }
    }

    private static int paginas(byte[] pdf) throws IOException {
        guardar(pdf);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    private static String texto(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    /** Cada página tem de ter conteúdo a sério: mais do que o rodapé e uma linha solta. */
    private static void assertSemPaginaQuaseVazia(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                long linhas = stripper.getText(doc).lines().filter(l -> !l.isBlank()).count();
                assertThat(linhas).as("linhas de texto na página %d de %d", p, doc.getNumberOfPages()).isGreaterThan(5);
            }
        }
    }
}
