package mz.netlineserv.chuangastudio.modules.invoices.service;

import mz.netlineserv.chuangastudio.shared.exceptions.RegraNegocioException;
import mz.netlineserv.chuangastudio.modules.invoices.domain.Fatura;
import mz.netlineserv.chuangastudio.modules.bookings.domain.Marcacao;
import mz.netlineserv.chuangastudio.modules.catalog.domain.Servico;
import mz.netlineserv.chuangastudio.modules.users.domain.Usuario;
import mz.netlineserv.chuangastudio.modules.invoices.domain.FormaPagamento;
import mz.netlineserv.chuangastudio.modules.invoices.domain.StatusFatura;
import mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao;
import mz.netlineserv.chuangastudio.modules.invoices.request.EmitirFaturaRequest;
import mz.netlineserv.chuangastudio.modules.invoices.vm.FaturaResponse;
import mz.netlineserv.chuangastudio.shared.config.DadosEstudio;
import mz.netlineserv.chuangastudio.modules.invoices.pdf.FaturaPdfRenderer;
import mz.netlineserv.chuangastudio.modules.notifications.event.NotificacaoFaturaEvent;
import mz.netlineserv.chuangastudio.modules.invoices.repository.FaturaRepository;
import mz.netlineserv.chuangastudio.modules.bookings.repository.MarcacaoRepository;
import mz.netlineserv.chuangastudio.modules.catalog.repository.ServicoRepository;
import mz.netlineserv.chuangastudio.modules.users.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** As contas da fatura: subtotal, desconto, adiantamento e total a pagar. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FaturaServiceTest {

    @Mock FaturaRepository faturaRepository;
    @Mock MarcacaoRepository marcacaoRepository;
    @Mock ServicoRepository servicoRepository;
    @Mock UsuarioService usuarioService;
    @Mock FaturaPdfRenderer faturaPdfRenderer;
    @Mock DadosEstudio dadosEstudio;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock mz.netlineserv.chuangastudio.modules.loyalty.service.FidelizacaoService fidelizacaoService;
    @Mock mz.netlineserv.chuangastudio.modules.loyalty.service.ValeService valeService;

    @InjectMocks FaturaService service;

    private Marcacao marcacao;
    private Servico manicure;
    private Servico verniz;

    @BeforeEach
    void preparar() {
        manicure = servico("Manicure Spa", "650.00");
        verniz = servico("Verniz Gel", "500.00");

        Usuario cliente = new Usuario();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Ana Teste");
        cliente.setEmail("ana@example.com");

        marcacao = new Marcacao();
        marcacao.setId(UUID.randomUUID());
        marcacao.setCliente(cliente);
        marcacao.setStatus(StatusMarcacao.CONFIRMADA);
        marcacao.setDataMarcacao(LocalDate.of(2026, 10, 5));
        marcacao.setHoraMarcacao(LocalTime.of(9, 0));
        marcacao.setValorTotal(new BigDecimal("1150.00"));
        marcacao.setValorAdiantamento(new BigDecimal("150.00"));
        marcacao.setAdiantamentoPago(true);

        when(marcacaoRepository.buscarPorIdComDetalhes(marcacao.getId())).thenReturn(Optional.of(marcacao));
        when(faturaRepository.proximoNumero()).thenReturn(7L);
        when(faturaRepository.save(any(Fatura.class))).thenAnswer(inv -> {
            Fatura f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });
        when(dadosEstudio.urlPdfFatura(anyString())).thenReturn("http://pdf");
        when(servicoRepository.findById(manicure.getId())).thenReturn(Optional.of(manicure));
        when(servicoRepository.findById(verniz.getId())).thenReturn(Optional.of(verniz));
    }

    @Test
    void descontaDescontoEAdiantamentoPago() {
        EmitirFaturaRequest pedido = pedido(item(manicure, 1), item(verniz, 2), extra("Decoração", "150.00"));
        pedido.setDesconto(new BigDecimal("100"));

        FaturaResponse fatura = service.emitir(marcacao.getId(), pedido);

        // 650 + 2×500 + 150 = 1800; − 100 desconto; − 150 adiantamento
        assertThat(fatura.getSubtotal()).isEqualByComparingTo("1800.00");
        assertThat(fatura.getDesconto()).isEqualByComparingTo("100.00");
        assertThat(fatura.getAdiantamentoDeduzido()).isEqualByComparingTo("150.00");
        assertThat(fatura.getTotalAPagar()).isEqualByComparingTo("1550.00");
        assertThat(fatura.getStatus()).isEqualTo("PENDENTE");
        assertThat(fatura.getNumero()).matches("FT \\d{4}/0007");
        assertThat(fatura.getItens()).hasSize(3);
        verify(eventPublisher).publishEvent(any(NotificacaoFaturaEvent.class));
    }

    @Test
    void concluiAMarcacaoComOValorRealmenteFaturado() {
        EmitirFaturaRequest pedido = pedido(item(manicure, 1));
        pedido.setDesconto(new BigDecimal("50"));

        service.emitir(marcacao.getId(), pedido);

        assertThat(marcacao.getStatus()).isEqualTo(StatusMarcacao.CONCLUIDA);
        assertThat(marcacao.getValorTotal()).isEqualByComparingTo("600.00");
    }

    @Test
    void adiantamentoPorConfirmarNaoEDescontado() {
        marcacao.setAdiantamentoPago(false);

        FaturaResponse fatura = service.emitir(marcacao.getId(), pedido(item(manicure, 1)));

        assertThat(fatura.getAdiantamentoDeduzido()).isEqualByComparingTo("0");
        assertThat(fatura.getTotalAPagar()).isEqualByComparingTo("650.00");
    }

    @Test
    void adiantamentoMaiorQueAContaFicaPagaAZero() {
        Servico corte = servico("Corte", "100.00");
        when(servicoRepository.findById(corte.getId())).thenReturn(Optional.of(corte));

        FaturaResponse fatura = service.emitir(marcacao.getId(), pedido(item(corte, 1)));

        assertThat(fatura.getAdiantamentoDeduzido()).isEqualByComparingTo("100.00");
        assertThat(fatura.getTotalAPagar()).isEqualByComparingTo("0");
        assertThat(fatura.getStatus()).isEqualTo(StatusFatura.PAGA.name());
    }

    @Test
    void comFormaDePagamentoSaiComoPaga() {
        EmitirFaturaRequest pedido = pedido(item(manicure, 1));
        pedido.setFormaPagamento(FormaPagamento.MPESA);
        pedido.setReferenciaPagamento("  ABC123  ");

        FaturaResponse fatura = service.emitir(marcacao.getId(), pedido);

        assertThat(fatura.getStatus()).isEqualTo("PAGA");
        assertThat(fatura.getFormaPagamento()).isEqualTo("MPESA");
        assertThat(fatura.getReferenciaPagamento()).isEqualTo("ABC123");
        assertThat(fatura.getPagoEm()).isNotNull();
    }

    @Test
    void recusaDescontoMaiorQueOSubtotal() {
        EmitirFaturaRequest pedido = pedido(item(manicure, 1));
        pedido.setDesconto(new BigDecimal("700"));

        assertThatThrownBy(() -> service.emitir(marcacao.getId(), pedido))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("desconto");
        verify(faturaRepository, never()).save(any());
    }

    @Test
    void recusaSegundaFaturaParaAMesmaMarcacao() {
        when(faturaRepository.existsByMarcacaoIdAndStatusNotAndDeletadoFalse(eq(marcacao.getId()), eq(StatusFatura.ANULADA)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.emitir(marcacao.getId(), pedido(item(manicure, 1))))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já tem uma fatura");
    }

    @Test
    void recusaMarcacaoCancelada() {
        marcacao.setStatus(StatusMarcacao.CANCELADA);

        assertThatThrownBy(() -> service.emitir(marcacao.getId(), pedido(item(manicure, 1))))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void extraSemPrecoERecusado() {
        EmitirFaturaRequest.Item semPreco = new EmitirFaturaRequest.Item();
        semPreco.setDescricao("Extra");

        assertThatThrownBy(() -> service.emitir(marcacao.getId(), pedido(semPreco)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("descrição e preço");
    }

    @Test
    void recompensaDeFidelizacaoEValeEntramNasContas() {
        when(fidelizacaoService.valorParaAplicar(marcacao.getCliente().getId())).thenReturn(new BigDecimal("500.00"));
        var vale = new mz.netlineserv.chuangastudio.modules.loyalty.domain.Vale();
        vale.setCodigo("CHG-TEST-0001");
        vale.setSaldo(new BigDecimal("300.00"));
        when(valeService.validarParaUso("CHG-TEST-0001")).thenReturn(vale);

        EmitirFaturaRequest pedido = pedido(item(manicure, 1), item(verniz, 1)); // 1150
        pedido.setAplicarFidelizacao(true);
        pedido.setCodigoVale("CHG-TEST-0001");
        FaturaResponse fatura = service.emitir(marcacao.getId(), pedido);

        // 1150 − 500 fidelização − 150 adiantamento − 300 vale = 200
        assertThat(fatura.getDescontoFidelizacao()).isEqualByComparingTo("500.00");
        assertThat(fatura.getValorVale()).isEqualByComparingTo("300.00");
        assertThat(fatura.getTotalAPagar()).isEqualByComparingTo("200.00");
        // A receita conta a fidelização como desconto, mas o vale é só forma de pagamento.
        assertThat(marcacao.getValorTotal()).isEqualByComparingTo("650.00");
        verify(valeService).usar(vale, new BigDecimal("300.00"));
    }

    @Test
    void valeComSaldoMaiorSoPagaOQueFalta() {
        var vale = new mz.netlineserv.chuangastudio.modules.loyalty.domain.Vale();
        vale.setCodigo("CHG-GRANDE");
        vale.setSaldo(new BigDecimal("5000.00"));
        when(valeService.validarParaUso("CHG-GRANDE")).thenReturn(vale);

        EmitirFaturaRequest pedido = pedido(item(manicure, 1)); // 650 − 150 adiantamento = 500
        pedido.setCodigoVale("CHG-GRANDE");
        FaturaResponse fatura = service.emitir(marcacao.getId(), pedido);

        assertThat(fatura.getValorVale()).isEqualByComparingTo("500.00");
        assertThat(fatura.getTotalAPagar()).isEqualByComparingTo("0");
        assertThat(fatura.getStatus()).isEqualTo("PAGA");
    }

    private static Servico servico(String nome, String preco) {
        Servico s = new Servico();
        s.setId(UUID.randomUUID());
        s.setNome(nome);
        s.setPreco(new BigDecimal(preco));
        s.setDuracaoMinutos(60);
        return s;
    }

    private static EmitirFaturaRequest.Item item(Servico servico, int quantidade) {
        EmitirFaturaRequest.Item item = new EmitirFaturaRequest.Item();
        item.setServicoId(servico.getId());
        item.setQuantidade(quantidade);
        return item;
    }

    private static EmitirFaturaRequest.Item extra(String descricao, String preco) {
        EmitirFaturaRequest.Item item = new EmitirFaturaRequest.Item();
        item.setDescricao(descricao);
        item.setPrecoUnitario(new BigDecimal(preco));
        return item;
    }

    private static EmitirFaturaRequest pedido(EmitirFaturaRequest.Item... itens) {
        EmitirFaturaRequest pedido = new EmitirFaturaRequest();
        pedido.setItens(List.of(itens));
        return pedido;
    }
}
