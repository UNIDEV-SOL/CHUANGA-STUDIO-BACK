package mz.netlineserv.chuangastudio.modules.quotations.vm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import mz.netlineserv.chuangastudio.modules.quotations.domain.Cotacao;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class CotacaoResponse {

    private UUID id;
    private String nome;
    private String telefone;
    private String email;
    private String mensagem;
    private BigDecimal totalEstimado;
    private String status;
    private LocalDateTime criadoEm;
    private List<String> servicosNomes;

    public static CotacaoResponse deEntidade(Cotacao cotacao) {
        return new CotacaoResponse(
                cotacao.getId(),
                cotacao.getNome(),
                cotacao.getTelefone(),
                cotacao.getEmail(),
                cotacao.getMensagem(),
                cotacao.getTotalEstimado(),
                cotacao.getStatus().name(),
                cotacao.getCriadoEm(),
                cotacao.getServicos().stream().map(s -> s.getNomeServico()).collect(Collectors.toList())
        );
    }
}
