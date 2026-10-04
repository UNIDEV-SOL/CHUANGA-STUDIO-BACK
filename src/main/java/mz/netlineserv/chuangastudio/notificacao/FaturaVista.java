package mz.netlineserv.chuangastudio.notificacao;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.domain.entity.Fatura;
import mz.netlineserv.chuangastudio.domain.entity.Marcacao;
import mz.netlineserv.chuangastudio.domain.entity.Usuario;
import mz.netlineserv.chuangastudio.domain.enums.StatusFatura;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static mz.netlineserv.chuangastudio.notificacao.Formatos.*;

/** Variáveis de uma fatura para os templates (PDF e emails), já formatadas. */
@Component
@RequiredArgsConstructor
public class FaturaVista {

    private final DadosEstudio dadosEstudio;

    public record Linha(String descricao, int quantidade, String precoUnitario, String total) {
    }

    public void preencher(Context ctx, Fatura fatura) {
        Marcacao marcacao = fatura.getMarcacao();
        Usuario cliente = marcacao.getCliente();

        Map<String, Object> f = new HashMap<>();
        f.put("numero", fatura.getNumero());
        f.put("pendente", fatura.getStatus() == StatusFatura.PENDENTE);
        f.put("paga", fatura.getStatus() == StatusFatura.PAGA);
        f.put("anulada", fatura.getStatus() == StatusFatura.ANULADA);
        f.put("dataEmissao", fatura.getDataEmissao().format(DATA_NUMERICA));
        f.put("dataEmissaoExtenso", fatura.getDataEmissao().format(DATA_CURTA));
        f.put("horaEmissao", fatura.getDataEmissao().format(HORA));
        f.put("subtotal", dinheiro(fatura.getSubtotal()));
        f.put("desconto", positivo(fatura.getDesconto()) ? "− " + dinheiro(fatura.getDesconto()) : null);
        f.put("adiantamento", positivo(fatura.getAdiantamentoDeduzido())
                ? "− " + dinheiro(fatura.getAdiantamentoDeduzido()) : null);
        f.put("fidelizacao", positivo(fatura.getDescontoFidelizacao()) ? "− " + dinheiro(fatura.getDescontoFidelizacao()) : null);
        f.put("vale", positivo(fatura.getValorVale()) ? "− " + dinheiro(fatura.getValorVale()) : null);
        f.put("valeCodigo", fatura.getVale() != null ? fatura.getVale().getCodigo() : null);
        f.put("total", dinheiro(fatura.getTotalAPagar()));
        f.put("formaPagamento", fatura.getFormaPagamento() != null ? fatura.getFormaPagamento().rotulo() : null);
        f.put("referencia", fatura.getReferenciaPagamento());
        f.put("pagoEm", fatura.getPagoEm() != null
                ? fatura.getPagoEm().format(DATA_NUMERICA) + " às " + fatura.getPagoEm().format(HORA) : null);
        f.put("observacoes", fatura.getObservacoes());
        f.put("pdfUrl", dadosEstudio.urlPdfFatura(fatura.getTokenPublico()));
        ctx.setVariable("fatura", f);

        Map<String, Object> c = new HashMap<>();
        c.put("nome", cliente.getNome());
        c.put("primeiroNome", primeiroNome(cliente.getNome()));
        c.put("telefone", cliente.getTelefone());
        c.put("email", cliente.getEmail());
        c.put("nuit", fatura.getClienteNuit());
        c.put("whatsappUrl", whatsappUrl(cliente.getTelefone()));
        ctx.setVariable("cliente", c);

        ctx.setVariable("atendimento", Map.of(
                "data", capitalizar(marcacao.getDataMarcacao().format(DATA_EXTENSO)),
                "dataCurta", marcacao.getDataMarcacao().format(DATA_NUMERICA),
                "hora", marcacao.getHoraMarcacao().format(HORA),
                "codigo", codigo(marcacao.getId())
        ));

        List<Linha> linhas = fatura.getItens().stream()
                .map(i -> new Linha(i.getDescricao(), i.getQuantidade(), dinheiro(i.getPrecoUnitario()), dinheiro(i.getTotal())))
                .toList();
        ctx.setVariable("linhas", linhas);
    }

    private static boolean positivo(BigDecimal valor) {
        return valor != null && valor.signum() > 0;
    }
}
