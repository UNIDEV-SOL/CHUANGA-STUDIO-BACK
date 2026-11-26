package mz.netlineserv.chuangastudio.modules.reports.pdf;

import mz.netlineserv.chuangastudio.modules.reports.vm.RelatorioCaixaResponse;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static mz.netlineserv.chuangastudio.shared.utils.Formatos.*;

/** Variáveis do relatório de caixa para o PDF e para o email, já formatadas. */
@Component
public class RelatorioVista {

    private static final DateTimeFormatter DIA_SEMANA = DateTimeFormatter.ofPattern("EEE dd/MM", PORTUGUES);
    private static final DateTimeFormatter CARIMBO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    /** Em períodos longos, os dias sem movimento só ocupam espaço. */
    private static final int DIAS_MOSTRAR_TODOS = 14;

    public record Linha(String rotulo, String valor, String detalhe, int percentagem) {
    }

    public void preencher(Context ctx, RelatorioCaixaResponse r, String titulo) {
        ctx.setVariable("titulo", titulo);
        ctx.setVariable("periodo", periodo(r.inicio(), r.fim()));
        ctx.setVariable("geradoEm", LocalDateTime.now(ZoneId.of("Africa/Maputo")).format(CARIMBO));
        ctx.setVariable("kpis", Map.of(
                "faturado", dinheiro(r.faturado()),
                "recebido", dinheiro(r.recebido()),
                "porReceber", dinheiro(r.porReceber()),
                "descontos", dinheiro(r.descontos()),
                "adiantamentos", dinheiro(r.adiantamentosDeduzidos()),
                "ticketMedio", dinheiro(r.ticketMedio()),
                "atendimentos", r.atendimentos(),
                "faturasPorReceber", r.faturasPorReceber(),
                "canceladas", r.marcacoesCanceladas()
        ));
        ctx.setVariable("semMovimento", r.atendimentos() == 0 && r.recebido().signum() == 0);

        // Até 2 semanas: um dia por linha. Mais do que isso: uma linha por semana (segunda a domingo),
        // para o PDF não crescer com o período nem deixar linhas soltas noutra página.
        boolean porSemana = r.porDia().size() > DIAS_MOSTRAR_TODOS;
        List<RelatorioCaixaResponse.PorDia> grupos = porSemana ? agruparPorSemana(r.porDia()) : r.porDia();
        BigDecimal maxDia = grupos.stream().map(RelatorioCaixaResponse.PorDia::faturado).reduce(BigDecimal.ONE, BigDecimal::max);
        List<Linha> dias = grupos.stream()
                .map(d -> new Linha(porSemana ? "Semana de " + d.data().format(DateTimeFormatter.ofPattern("dd/MM")) : capitalizar(d.data().format(DIA_SEMANA)),
                        dinheiro(d.faturado()),
                        d.recebido().signum() > 0 ? "recebido " + dinheiro(d.recebido()) : "",
                        percentagem(d.faturado(), maxDia)))
                .toList();
        ctx.setVariable("dias", r.porDia().size() > 1 ? dias : List.of());
        ctx.setVariable("porSemana", porSemana);

        BigDecimal maxForma = r.porForma().stream().map(RelatorioCaixaResponse.PorForma::total).reduce(BigDecimal.ONE, BigDecimal::max);
        ctx.setVariable("formas", r.porForma().stream()
                .map(f -> new Linha(f.rotulo(), dinheiro(f.total()), f.quantidade() + (f.quantidade() == 1 ? " pagamento" : " pagamentos"),
                        percentagem(f.total(), maxForma)))
                .toList());
        ctx.setVariable("servicos", r.topServicos().stream()
                .limit(8) // cabe numa página A4 com a semana inteira
                .map(s -> new Linha(s.descricao(), dinheiro(s.total()), String.valueOf(s.quantidade()), 0))
                .toList());
    }

    /** Soma os dias por semana; a data de cada grupo é a segunda-feira (ou o início do período). */
    private static List<RelatorioCaixaResponse.PorDia> agruparPorSemana(List<RelatorioCaixaResponse.PorDia> dias) {
        java.util.Map<LocalDate, RelatorioCaixaResponse.PorDia> semanas = new java.util.LinkedHashMap<>();
        LocalDate primeiro = dias.get(0).data();
        for (RelatorioCaixaResponse.PorDia d : dias) {
            LocalDate segunda = d.data().with(java.time.DayOfWeek.MONDAY);
            LocalDate chave = segunda.isBefore(primeiro) ? primeiro : segunda;
            semanas.merge(chave, d, (a, b) -> new RelatorioCaixaResponse.PorDia(a.data(),
                    a.faturado().add(b.faturado()), a.recebido().add(b.recebido())));
        }
        return List.copyOf(semanas.values());
    }

    public static String periodo(LocalDate inicio, LocalDate fim) {
        if (inicio.equals(fim)) {
            return capitalizar(inicio.format(DATA_EXTENSO));
        }
        String ini = inicio.getYear() == fim.getYear()
                ? inicio.format(DateTimeFormatter.ofPattern("d 'de' MMMM", PORTUGUES))
                : inicio.format(DATA_CURTA);
        return ini + " a " + fim.format(DATA_CURTA);
    }

    private static int percentagem(BigDecimal valor, BigDecimal maximo) {
        if (maximo.signum() <= 0) {
            return 0;
        }
        return valor.multiply(BigDecimal.valueOf(100)).divide(maximo, 0, java.math.RoundingMode.HALF_UP).intValue();
    }
}
