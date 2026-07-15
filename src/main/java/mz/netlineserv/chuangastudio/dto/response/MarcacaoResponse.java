package mz.netlineserv.chuangastudio.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import mz.netlineserv.chuangastudio.domain.entity.Marcacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class MarcacaoResponse {

    private UUID id;
    private UUID clienteId;
    private String clienteNome;
    private String clienteTelefone;
    private LocalDate data;
    private LocalTime hora;
    private String status;
    private BigDecimal valorTotal;
    private BigDecimal valorAdiantamento;
    private boolean adiantamentoPago;
    private String metodoPagamento;
    private List<MarcacaoServicoResponse> servicos;

    public static MarcacaoResponse deEntidade(Marcacao marcacao) {
        return new MarcacaoResponse(
                marcacao.getId(),
                marcacao.getCliente().getId(),
                marcacao.getCliente().getNome(),
                marcacao.getCliente().getTelefone(),
                marcacao.getDataMarcacao(),
                marcacao.getHoraMarcacao(),
                marcacao.getStatus().name(),
                marcacao.getValorTotal(),
                marcacao.getValorAdiantamento(),
                marcacao.isAdiantamentoPago(),
                marcacao.getMetodoPagamento() != null ? marcacao.getMetodoPagamento().name() : null,
                marcacao.getServicos().stream().map(MarcacaoServicoResponse::deEntidade).collect(Collectors.toList())
        );
    }
}
