package mz.netlineserv.chuangastudio.notificacao;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.domain.entity.Cotacao;
import mz.netlineserv.chuangastudio.domain.entity.Fatura;
import mz.netlineserv.chuangastudio.domain.entity.Marcacao;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.MetodoPagamento;
import mz.netlineserv.chuangastudio.domain.enums.StatusFatura;
import mz.netlineserv.chuangastudio.dto.response.RelatorioCaixaResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static mz.netlineserv.chuangastudio.notificacao.Formatos.*;

/** Transforma marcações, cotações e faturas em emails HTML a partir dos templates em templates/email. */
@Component
@RequiredArgsConstructor
public class EmailComposer {

    private final ITemplateEngine templateEngine;
    private final DadosEstudio dadosEstudio;
    private final FaturaVista faturaVista;
    private final RelatorioVista relatorioVista;

    @Value("${app.email.estudio:}")
    private String emailEstudio;

    public record Item(String nome, String preco) {
    }

    public EmailMensagem comporMarcacao(Marcacao marcacao, NotificacaoMarcacaoEvent.Tipo tipo) {
        return comporMarcacao(marcacao, tipo, null);
    }

    public EmailMensagem comporMarcacao(Marcacao marcacao, NotificacaoMarcacaoEvent.Tipo tipo, LocalDateTime anterior) {
        Context ctx = contextoBase();
        ctx.setVariable("nome", primeiroNome(marcacao.getCliente().getNome()));
        ctx.setVariable("codigo", codigo(marcacao.getId()));
        ctx.setVariable("data", capitalizar(marcacao.getDataMarcacao().format(DATA_EXTENSO)));
        ctx.setVariable("hora", marcacao.getHoraMarcacao().format(HORA));
        ctx.setVariable("itens", itens(marcacao));
        ctx.setVariable("total", dinheiro(marcacao.getValorTotal()));
        ctx.setVariable("adiantamento", dinheiro(marcacao.getValorAdiantamento()));
        ctx.setVariable("restante", dinheiro(marcacao.getValorTotal().subtract(marcacao.getValorAdiantamento()).max(BigDecimal.ZERO)));
        ctx.setVariable("adiantamentoPago", marcacao.isAdiantamentoPago());
        ctx.setVariable("metodo", rotulo(marcacao.getMetodoPagamento()));
        ctx.setVariable("referencia", marcacao.getReferenciaPagamento());
        ctx.setVariable("observacoes", marcacao.getObservacoes());
        ctx.setVariable("calendarioUrl", Calendario.googleUrl(marcacao, localizacao()));
        LocalDate hoje = LocalDate.now(java.time.ZoneId.of("Africa/Maputo"));
        String quando = marcacao.getDataMarcacao().equals(hoje) ? "hoje"
                : marcacao.getDataMarcacao().equals(hoje.plusDays(1)) ? "amanhã"
                : "no dia " + marcacao.getDataMarcacao().format(DATA_CURTA);
        ctx.setVariable("quando", quando);
        ctx.setVariable("anterior", anterior != null
                ? anterior.format(DATA_CURTA) + " às " + anterior.format(HORA) : null);

        String dataCurta = marcacao.getDataMarcacao().format(DATA_CURTA) + " às " + marcacao.getHoraMarcacao().format(HORA);
        String assunto = switch (tipo) {
            case RECEBIDA -> "Recebemos o seu pedido de marcação · " + dataCurta;
            case CONFIRMADA -> "A sua marcação está confirmada · " + dataCurta;
            case CANCELADA -> "A sua marcação foi cancelada · " + dataCurta;
            case REMARCADA -> "A sua marcação mudou para " + dataCurta;
            case LEMBRETE -> "Lembrete: a sua marcação é " + quando + " às " + marcacao.getHoraMarcacao().format(HORA);
        };
        String template = switch (tipo) {
            case RECEBIDA -> "email/marcacao-recebida";
            case CONFIRMADA -> "email/marcacao-confirmada";
            case CANCELADA -> "email/marcacao-cancelada";
            case REMARCADA -> "email/marcacao-remarcada";
            case LEMBRETE -> "email/marcacao-lembrete";
        };
        ctx.setVariable("assunto", assunto);

        // Confirmada ou com nova hora: leva o evento para o calendário do telemóvel (Apple/Outlook).
        List<EmailMensagem.Anexo> anexos = tipo == NotificacaoMarcacaoEvent.Tipo.CONFIRMADA
                || tipo == NotificacaoMarcacaoEvent.Tipo.REMARCADA
                ? List.of(new EmailMensagem.Anexo("marcacao-chuanga-studio.ics",
                        Calendario.ics(marcacao, localizacao(), urlSite()).getBytes(StandardCharsets.UTF_8)))
                : List.of();
        return new EmailMensagem(marcacao.getCliente().getEmail(), assunto, templateEngine.process(template, ctx), null, anexos);
    }

    public EmailMensagem comporCotacao(Cotacao cotacao) {
        Context ctx = contextoBase();
        ctx.setVariable("nome", primeiroNome(cotacao.getNome()));
        ctx.setVariable("codigo", codigo(cotacao.getId()));
        ctx.setVariable("dataPedido", cotacao.getCriadoEm() != null
                ? cotacao.getCriadoEm().toLocalDate().format(DATA_CURTA)
                : LocalDate.now().format(DATA_CURTA));
        ctx.setVariable("itens", cotacao.getServicos().stream()
                .map(i -> new Item(i.getNomeServico(), dinheiro(i.getPrecoNoMomento())))
                .toList());
        ctx.setVariable("total", dinheiro(cotacao.getTotalEstimado()));
        ctx.setVariable("mensagem", cotacao.getMensagem());

        String assunto = "A sua cotação Chuanga Studio · " + dinheiro(cotacao.getTotalEstimado());
        ctx.setVariable("assunto", assunto);

        return new EmailMensagem(cotacao.getEmail(), assunto, templateEngine.process("email/cotacao", ctx));
    }

    /** Aviso ao estúdio sobre uma marcação nova ou cancelada pelo cliente. Vazio se EMAIL_ESTUDIO não estiver definido. */
    public Optional<EmailMensagem> comporAvisoEstudio(Marcacao marcacao, NotificacaoMarcacaoEvent.Tipo tipo) {
        return comporAvisoEstudio(marcacao, tipo, null);
    }

    public Optional<EmailMensagem> comporAvisoEstudio(Marcacao marcacao, NotificacaoMarcacaoEvent.Tipo tipo, LocalDateTime anterior) {
        if (emailEstudio == null || emailEstudio.isBlank() || tipo == NotificacaoMarcacaoEvent.Tipo.CONFIRMADA
                || tipo == NotificacaoMarcacaoEvent.Tipo.LEMBRETE) {
            return Optional.empty();
        }
        boolean cancelada = tipo == NotificacaoMarcacaoEvent.Tipo.CANCELADA;
        boolean remarcada = tipo == NotificacaoMarcacaoEvent.Tipo.REMARCADA;
        Usuario cliente = marcacao.getCliente();

        Context ctx = contextoBase();
        ctx.setVariable("notaRodape", "Aviso automático do site Chuanga Studio para a equipa.");
        ctx.setVariable("cancelada", cancelada);
        ctx.setVariable("remarcada", remarcada);
        ctx.setVariable("anterior", anterior != null
                ? anterior.format(DATA_CURTA) + " às " + anterior.format(HORA) : null);
        ctx.setVariable("clienteNome", cliente.getNome());
        ctx.setVariable("clienteTelefone", cliente.getTelefone());
        ctx.setVariable("clienteEmail", cliente.getEmail());
        ctx.setVariable("clienteWhatsappUrl", whatsappUrl(cliente.getTelefone()));
        ctx.setVariable("codigo", codigo(marcacao.getId()));
        ctx.setVariable("data", capitalizar(marcacao.getDataMarcacao().format(DATA_EXTENSO)));
        ctx.setVariable("hora", marcacao.getHoraMarcacao().format(HORA));
        ctx.setVariable("itens", itens(marcacao));
        ctx.setVariable("total", dinheiro(marcacao.getValorTotal()));
        ctx.setVariable("adiantamento", dinheiro(marcacao.getValorAdiantamento()));
        ctx.setVariable("metodo", rotulo(marcacao.getMetodoPagamento()));
        ctx.setVariable("referencia", marcacao.getReferenciaPagamento());
        ctx.setVariable("observacoes", marcacao.getObservacoes());
        ctx.setVariable("painelUrl", urlSite() + "/admin/marcacoes");

        String quando = marcacao.getDataMarcacao().format(DATA_CURTA) + " às " + marcacao.getHoraMarcacao().format(HORA);
        String assunto = (cancelada ? "Marcação cancelada pelo cliente · " : remarcada ? "Marcação remarcada · " : "Nova marcação · ")
                + cliente.getNome() + " · " + quando;
        ctx.setVariable("assunto", assunto);

        return Optional.of(new EmailMensagem(emailEstudio, assunto,
                templateEngine.process("email/estudio-marcacao", ctx), cliente.getEmail()));
    }

    /** Resumo das marcações do dia seguinte para a equipa. Vazio se EMAIL_ESTUDIO não estiver definido. */
    public Optional<EmailMensagem> comporAgendaEstudio(LocalDate dia, List<Marcacao> marcacoes) {
        if (emailEstudio == null || emailEstudio.isBlank()) {
            return Optional.empty();
        }
        Context ctx = contextoBase();
        ctx.setVariable("notaRodape", "Aviso automático do site Chuanga Studio para a equipa.");
        ctx.setVariable("dia", capitalizar(dia.format(DATA_EXTENSO)));
        ctx.setVariable("painelUrl", urlSite() + "/admin/marcacoes");
        ctx.setVariable("marcacoes", marcacoes.stream().map(m -> Map.of(
                "hora", m.getHoraMarcacao().format(HORA),
                "cliente", m.getCliente().getNome(),
                "telefone", m.getCliente().getTelefone() != null ? m.getCliente().getTelefone() : "—",
                "servicos", Calendario.descricao(m).replaceAll(" · Pedido nº .*$", ""),
                "porConfirmar", m.getStatus() == mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao.AGUARDA_CONFIRMACAO,
                "total", dinheiro(m.getValorTotal())
        )).toList());
        String assunto = "Agenda de amanhã · " + marcacoes.size() + (marcacoes.size() == 1 ? " marcação" : " marcações")
                + " · " + dia.format(DATA_CURTA);
        ctx.setVariable("assunto", assunto);
        return Optional.of(new EmailMensagem(emailEstudio, assunto, templateEngine.process("email/estudio-agenda", ctx)));
    }

    /** Relatório de caixa (diário ou semanal) para a equipa, com o PDF em anexo. */
    public Optional<EmailMensagem> comporRelatorioEstudio(RelatorioCaixaResponse relatorio, String titulo, byte[] pdf) {
        if (emailEstudio == null || emailEstudio.isBlank()) {
            return Optional.empty();
        }
        Context ctx = contextoBase();
        relatorioVista.preencher(ctx, relatorio, titulo);
        ctx.setVariable("notaRodape", "Relatório automático do site Chuanga Studio para a equipa.");
        ctx.setVariable("painelUrl", urlSite() + "/admin/relatorios");
        String assunto = titulo + " · " + RelatorioVista.periodo(relatorio.inicio(), relatorio.fim())
                + " · " + dinheiro(relatorio.faturado()) + " faturado";
        ctx.setVariable("assunto", assunto);
        return Optional.of(new EmailMensagem(emailEstudio, assunto, templateEngine.process("email/estudio-relatorio", ctx),
                null, List.of(new EmailMensagem.Anexo(RelatorioPdfRenderer.nomeFicheiro(relatorio), pdf))));
    }

    /** Aviso ao estúdio sobre um pedido de cotação novo. Vazio se EMAIL_ESTUDIO não estiver definido. */
    public Optional<EmailMensagem> comporAvisoEstudio(Cotacao cotacao) {
        if (emailEstudio == null || emailEstudio.isBlank()) {
            return Optional.empty();
        }
        Context ctx = contextoBase();
        ctx.setVariable("notaRodape", "Aviso automático do site Chuanga Studio para a equipa.");
        ctx.setVariable("clienteNome", cotacao.getNome());
        ctx.setVariable("clienteTelefone", cotacao.getTelefone());
        ctx.setVariable("clienteEmail", cotacao.getEmail());
        ctx.setVariable("clienteWhatsappUrl", whatsappUrl(cotacao.getTelefone()));
        ctx.setVariable("codigo", codigo(cotacao.getId()));
        ctx.setVariable("itens", cotacao.getServicos().stream()
                .map(i -> new Item(i.getNomeServico(), dinheiro(i.getPrecoNoMomento())))
                .toList());
        ctx.setVariable("total", dinheiro(cotacao.getTotalEstimado()));
        ctx.setVariable("mensagem", cotacao.getMensagem());
        ctx.setVariable("painelUrl", urlSite() + "/admin/cotacoes");

        String assunto = "Novo pedido de cotação · " + cotacao.getNome() + " · " + dinheiro(cotacao.getTotalEstimado());
        ctx.setVariable("assunto", assunto);

        return Optional.of(new EmailMensagem(emailEstudio, assunto,
                templateEngine.process("email/estudio-cotacao", ctx), cotacao.getEmail()));
    }

    public EmailMensagem comporRecuperacaoSenha(Usuario usuario, String token, int validadeMinutos) {
        Context ctx = contextoBase();
        ctx.setVariable("nome", primeiroNome(usuario.getNome()));
        ctx.setVariable("link", urlSite() + "/redefinir-senha?token=" + token);
        ctx.setVariable("validadeMinutos", validadeMinutos);
        ctx.setVariable("notaRodape", "Recebeu este email porque alguém pediu para redefinir a palavra-passe da sua conta.");
        String assunto = "Redefinir a palavra-passe · Chuanga Studio";
        ctx.setVariable("assunto", assunto);
        return new EmailMensagem(usuario.getEmail(), assunto, templateEngine.process("email/recuperar-senha", ctx));
    }

    public EmailMensagem comporPedidoAvaliacao(Marcacao marcacao, String token) {
        Context ctx = contextoBase();
        ctx.setVariable("nome", primeiroNome(marcacao.getCliente().getNome()));
        ctx.setVariable("data", capitalizar(marcacao.getDataMarcacao().format(DATA_EXTENSO)));
        ctx.setVariable("servicos", String.join(", ", itens(marcacao).stream().map(Item::nome).toList()));
        String link = urlSite() + "/avaliar/" + token;
        ctx.setVariable("link", link);
        ctx.setVariable("estrelas", java.util.stream.IntStream.rangeClosed(1, 5).mapToObj(n -> new Item(String.valueOf(n), link + "?nota=" + n)).toList());
        ctx.setVariable("notaRodape", "Recebeu este email porque foi atendida no Chuanga Studio.");
        String assunto = "Como correu, " + primeiroNome(marcacao.getCliente().getNome()) + "? · Chuanga Studio";
        ctx.setVariable("assunto", assunto);
        return new EmailMensagem(marcacao.getCliente().getEmail(), assunto, templateEngine.process("email/avaliacao-pedido", ctx));
    }

    public EmailMensagem comporLembreteManutencao(Marcacao marcacao, String servico, int dias) {
        Context ctx = contextoBase();
        ctx.setVariable("nome", primeiroNome(marcacao.getCliente().getNome()));
        ctx.setVariable("servico", servico);
        ctx.setVariable("semanas", Math.max(1, Math.round(dias / 7f)));
        ctx.setVariable("data", capitalizar(marcacao.getDataMarcacao().format(DATA_CURTA)));
        ctx.setVariable("link", urlSite() + "/reservar");
        ctx.setVariable("notaRodape", "Recebeu este email porque fez " + servico + " no Chuanga Studio.");
        String assunto = "Está na hora da manutenção 💅 · Chuanga Studio";
        ctx.setVariable("assunto", assunto);
        return new EmailMensagem(marcacao.getCliente().getEmail(), assunto, templateEngine.process("email/manutencao-lembrete", ctx));
    }

    public EmailMensagem comporVale(mz.netlineserv.chuangastudio.fidelizacao.Vale vale) {
        Context ctx = contextoBase();
        ctx.setVariable("nome", vale.getBeneficiarioNome() != null ? primeiroNome(vale.getBeneficiarioNome()) : null);
        ctx.setVariable("de", vale.getCompradorNome());
        ctx.setVariable("mensagem", vale.getMensagem());
        ctx.setVariable("codigo", vale.getCodigo());
        ctx.setVariable("valor", dinheiro(vale.getValorInicial()));
        ctx.setVariable("validoAte", vale.getValidoAte() != null ? vale.getValidoAte().format(DATA_CURTA) : null);
        ctx.setVariable("link", urlSite() + "/vales?codigo=" + vale.getCodigo());
        ctx.setVariable("notaRodape", "Recebeu este email porque alguém lhe ofereceu um vale do Chuanga Studio.");
        String assunto = "Tem um vale-oferta de " + dinheiro(vale.getValorInicial()) + " no Chuanga Studio 🎁";
        ctx.setVariable("assunto", assunto);
        return new EmailMensagem(vale.getBeneficiarioEmail(), assunto, templateEngine.process("email/vale-oferta", ctx));
    }

    /** Fatura (pendente) ou recibo (paga) para o cliente, com o PDF em anexo. */
    public EmailMensagem comporFatura(Fatura fatura, byte[] pdf, String para) {
        Context ctx = contextoFatura(fatura);
        String assunto = fatura.getStatus() == StatusFatura.PAGA
                ? "Recibo Chuanga Studio · Fatura " + fatura.getNumero() + " paga"
                : "A sua fatura Chuanga Studio · " + fatura.getNumero() + " · " + dinheiro(fatura.getTotalAPagar());
        ctx.setVariable("assunto", assunto);
        return new EmailMensagem(para, assunto, templateEngine.process("email/fatura", ctx), null, anexoPdf(fatura, pdf));
    }

    /** Cópia da fatura para o estúdio. Vazio se EMAIL_ESTUDIO não estiver definido. */
    public Optional<EmailMensagem> comporAvisoEstudio(Fatura fatura, byte[] pdf) {
        if (emailEstudio == null || emailEstudio.isBlank()) {
            return Optional.empty();
        }
        Context ctx = contextoFatura(fatura);
        ctx.setVariable("notaRodape", "Aviso automático do site Chuanga Studio para a equipa.");
        ctx.setVariable("painelUrl", urlSite() + "/admin/faturas");
        Usuario cliente = fatura.getMarcacao().getCliente();
        String assunto = (fatura.getStatus() == StatusFatura.PAGA ? "Fatura paga · " : "Fatura emitida · ")
                + fatura.getNumero() + " · " + cliente.getNome() + " · " + dinheiro(fatura.getTotalAPagar());
        ctx.setVariable("assunto", assunto);
        return Optional.of(new EmailMensagem(emailEstudio, assunto,
                templateEngine.process("email/estudio-fatura", ctx), cliente.getEmail(), anexoPdf(fatura, pdf)));
    }

    private Context contextoFatura(Fatura fatura) {
        Context ctx = contextoBase();
        faturaVista.preencher(ctx, fatura);
        ctx.setVariable("itens", fatura.getItens().stream()
                .map(i -> new Item(i.getQuantidade() > 1 ? i.getQuantidade() + " × " + i.getDescricao() : i.getDescricao(),
                        dinheiro(i.getTotal())))
                .toList());
        return ctx;
    }

    private static List<EmailMensagem.Anexo> anexoPdf(Fatura fatura, byte[] pdf) {
        return List.of(new EmailMensagem.Anexo(FaturaPdfRenderer.nomeFicheiro(fatura), pdf));
    }

    private Context contextoBase() {
        Context ctx = new Context(PORTUGUES);
        ctx.setVariable("estudio", dadosEstudio.paraTemplate());
        return ctx;
    }

    private String localizacao() {
        return String.valueOf(dadosEstudio.paraTemplate().get("localizacao"));
    }

    private String urlSite() {
        return dadosEstudio.urlSite();
    }

    private static List<Item> itens(Marcacao marcacao) {
        return marcacao.getServicos().stream()
                .map(i -> new Item(i.getNomeServico(), dinheiro(i.getPrecoNoMomento())))
                .toList();
    }

    private static String rotulo(MetodoPagamento metodo) {
        if (metodo == null) {
            return null;
        }
        return switch (metodo) {
            case MPESA -> "M-Pesa";
            case EMOLA -> "e-Mola";
        };
    }
}
