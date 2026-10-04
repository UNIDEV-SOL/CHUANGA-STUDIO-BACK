package mz.netlineserv.chuangastudio.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import mz.netlineserv.chuangastudio.domain.entity.Servico;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ServicoResponse {

    private UUID id;
    private String nome;
    private String categoria;
    private String descricao;
    private BigDecimal preco;
    private Integer duracaoMinutos;
    private boolean destaque;
    private boolean ativo;
    private Integer manutencaoDias;

    public static ServicoResponse deEntidade(Servico servico) {
        return new ServicoResponse(
                servico.getId(),
                servico.getNome(),
                servico.getCategoria(),
                servico.getDescricao(),
                servico.getPreco(),
                servico.getDuracaoMinutos(),
                servico.isDestaque(),
                servico.isAtivo(),
                servico.getManutencaoDias()
        );
    }
}
