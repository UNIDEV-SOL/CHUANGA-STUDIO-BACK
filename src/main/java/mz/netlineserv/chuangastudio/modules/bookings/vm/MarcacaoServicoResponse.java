package mz.netlineserv.chuangastudio.modules.bookings.vm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import mz.netlineserv.chuangastudio.modules.bookings.domain.MarcacaoServico;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class MarcacaoServicoResponse {
    private UUID servicoId;
    private String nome;
    private BigDecimal preco;

    public static MarcacaoServicoResponse deEntidade(MarcacaoServico item) {
        return new MarcacaoServicoResponse(item.getServico().getId(), item.getNomeServico(), item.getPrecoNoMomento());
    }
}
