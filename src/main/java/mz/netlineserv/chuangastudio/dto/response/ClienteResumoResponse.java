package mz.netlineserv.chuangastudio.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ClienteResumoResponse {
    private UUID id;
    private String nome;
    private String email;
    private String telefone;
    private LocalDateTime criadoEm;
    private long totalMarcacoes;
    private BigDecimal totalInvestido;
}
