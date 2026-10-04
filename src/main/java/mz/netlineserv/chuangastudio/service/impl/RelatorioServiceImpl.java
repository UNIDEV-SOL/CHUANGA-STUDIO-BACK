package mz.netlineserv.chuangastudio.service.impl;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import mz.netlineserv.chuangastudio.domain.entity.Fatura;
import mz.netlineserv.chuangastudio.domain.entity.FaturaItem;
import mz.netlineserv.chuangastudio.domain.enums.FormaPagamento;
import mz.netlineserv.chuangastudio.domain.enums.StatusFatura;
import mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao;
import mz.netlineserv.chuangastudio.dto.response.RelatorioCaixaResponse;
import mz.netlineserv.chuangastudio.repository.FaturaRepository;
import mz.netlineserv.chuangastudio.repository.MarcacaoRepository;
import mz.netlineserv.chuangastudio.service.RelatorioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelatorioServiceImpl implements RelatorioService {

    private static final int MAX_DIAS = 366;

    private final FaturaRepository faturaRepository;
    private final MarcacaoRepository marcacaoRepository;

    @Override
    @Transactional(readOnly = true)
    public RelatorioCaixaResponse caixa(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio)) {
            throw new RegraNegocioException("A data final tem de ser igual ou depois da inicial.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) > MAX_DIAS) {
            throw new RegraNegocioException("Escolha um período de até um ano.");
        }

        // O volume de um estúdio é pequeno: agregar em memória mantém as regras num só sítio.
        List<Fatura> todas = faturaRepository.buscarTodasComDetalhes();
        List<Fatura> emitidas = todas.stream()
                .filter(f -> f.getStatus() != StatusFatura.ANULADA)
                .filter(f -> noPeriodo(f.getDataEmissao().toLocalDate(), inicio, fim))
                .toList();
        List<Fatura> pagas = todas.stream()
                .filter(f -> f.getStatus() == StatusFatura.PAGA && f.getPagoEm() != null)
                .filter(f -> noPeriodo(f.getPagoEm().toLocalDate(), inicio, fim))
                .toList();
        List<Fatura> pendentes = todas.stream().filter(f -> f.getStatus() == StatusFatura.PENDENTE).toList();

        BigDecimal faturado = somar(emitidas, f -> f.getSubtotal().subtract(f.getDesconto()).subtract(f.getDescontoFidelizacao()));
        BigDecimal recebido = somar(pagas, Fatura::getTotalAPagar);
        long atendimentos = emitidas.size();
        BigDecimal ticketMedio = atendimentos == 0 ? BigDecimal.ZERO
                : faturado.divide(BigDecimal.valueOf(atendimentos), 2, RoundingMode.HALF_UP);

        long canceladas = marcacaoRepository.findByStatusAndDeletadoFalseOrderByDataMarcacaoDescHoraMarcacaoDesc(StatusMarcacao.CANCELADA)
                .stream().filter(m -> noPeriodo(m.getDataMarcacao(), inicio, fim)).count();

        return new RelatorioCaixaResponse(
                inicio, fim,
                faturado,
                recebido,
                somar(emitidas, f -> f.getDesconto().add(f.getDescontoFidelizacao())),
                somar(emitidas, Fatura::getAdiantamentoDeduzido),
                somar(pendentes, Fatura::getTotalAPagar),
                pendentes.size(),
                atendimentos,
                ticketMedio,
                canceladas,
                porForma(pagas),
                porDia(emitidas, pagas, inicio, fim),
                topServicos(emitidas));
    }

    private static List<RelatorioCaixaResponse.PorForma> porForma(List<Fatura> pagas) {
        Map<FormaPagamento, List<Fatura>> grupos = pagas.stream()
                .filter(f -> f.getFormaPagamento() != null)
                .collect(Collectors.groupingBy(Fatura::getFormaPagamento));
        List<RelatorioCaixaResponse.PorForma> lista = new ArrayList<>(grupos.entrySet().stream()
                .map(e -> new RelatorioCaixaResponse.PorForma(e.getKey().name(), e.getKey().rotulo(),
                        somar(e.getValue(), Fatura::getTotalAPagar), e.getValue().size()))
                .toList());
        // Faturas fechadas a zero (adiantamento cobriu tudo) não têm forma de pagamento.
        long semForma = pagas.stream().filter(f -> f.getFormaPagamento() == null).count();
        if (semForma > 0) {
            lista.add(new RelatorioCaixaResponse.PorForma("SEM_PAGAMENTO", "Coberto pelo adiantamento",
                    somar(pagas.stream().filter(f -> f.getFormaPagamento() == null).toList(), Fatura::getTotalAPagar), semForma));
        }
        lista.sort(Comparator.comparing(RelatorioCaixaResponse.PorForma::total).reversed());
        return lista;
    }

    private static List<RelatorioCaixaResponse.PorDia> porDia(List<Fatura> emitidas, List<Fatura> pagas,
                                                             LocalDate inicio, LocalDate fim) {
        Map<LocalDate, BigDecimal> faturadoPorDia = emitidas.stream().collect(Collectors.groupingBy(
                f -> f.getDataEmissao().toLocalDate(),
                Collectors.reducing(BigDecimal.ZERO, f -> f.getSubtotal().subtract(f.getDesconto()).subtract(f.getDescontoFidelizacao()), BigDecimal::add)));
        Map<LocalDate, BigDecimal> recebidoPorDia = pagas.stream().collect(Collectors.groupingBy(
                f -> f.getPagoEm().toLocalDate(),
                Collectors.reducing(BigDecimal.ZERO, Fatura::getTotalAPagar, BigDecimal::add)));
        List<RelatorioCaixaResponse.PorDia> dias = new ArrayList<>();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            dias.add(new RelatorioCaixaResponse.PorDia(d,
                    faturadoPorDia.getOrDefault(d, BigDecimal.ZERO), recebidoPorDia.getOrDefault(d, BigDecimal.ZERO)));
        }
        return dias;
    }

    private static List<RelatorioCaixaResponse.PorServico> topServicos(List<Fatura> emitidas) {
        Map<String, List<FaturaItem>> porDescricao = emitidas.stream()
                .flatMap(f -> f.getItens().stream())
                .collect(Collectors.groupingBy(FaturaItem::getDescricao, LinkedHashMap::new, Collectors.toList()));
        return porDescricao.entrySet().stream()
                .map(e -> new RelatorioCaixaResponse.PorServico(e.getKey(),
                        e.getValue().stream().mapToLong(FaturaItem::getQuantidade).sum(),
                        e.getValue().stream().map(FaturaItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(RelatorioCaixaResponse.PorServico::total).reversed())
                .limit(10)
                .toList();
    }

    private static BigDecimal somar(List<Fatura> faturas, Function<Fatura, BigDecimal> valor) {
        return faturas.stream().map(valor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean noPeriodo(LocalDate data, LocalDate inicio, LocalDate fim) {
        return !data.isBefore(inicio) && !data.isAfter(fim);
    }
}
