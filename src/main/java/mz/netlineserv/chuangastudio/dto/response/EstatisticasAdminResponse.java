package mz.netlineserv.chuangastudio.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class EstatisticasAdminResponse {
    private long marcacoesHoje;
    private BigDecimal receitaDoMes;
    private long totalClientes;
    private long totalCotacoes;
}
