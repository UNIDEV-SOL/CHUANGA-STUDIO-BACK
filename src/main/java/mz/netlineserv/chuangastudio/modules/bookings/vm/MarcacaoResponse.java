package mz.netlineserv.chuangastudio.modules.bookings.vm;

import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import mz.netlineserv.chuangastudio.modules.bookings.domain.Marcacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    private String observacoes;
    private LocalDateTime criadoEm;
    /** Soma das durações dos serviços (para o calendário e a agenda). */
    private int duracaoMinutos;
    private UUID profissionalId;
    private String profissionalNome;
    private List<MarcacaoServicoResponse> servicos;

    // Só na lista do painel (preenchidos pelo serviço para a página inteira de uma vez).
    @Setter private UUID faturaId;
    @Setter private String faturaNumero;
    @Setter private String faturaStatus;
    @Setter private Long clienteFaltas;

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
                marcacao.getObservacoes(),
                marcacao.getCriadoEm(),
                marcacao.getServicos().stream().mapToInt(i -> i.getServico().getDuracaoMinutos()).sum(),
                marcacao.getProfissional() != null ? marcacao.getProfissional().getId() : null,
                marcacao.getProfissional() != null ? marcacao.getProfissional().getNome() : null,
                marcacao.getServicos().stream().map(MarcacaoServicoResponse::deEntidade).collect(Collectors.toList()),
                null, null, null, null
        );
    }
}
