package mz.netlineserv.chuangastudio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ClienteResumoResponse(
        UUID id,
        String nome,
        String email,
        String telefone,
        LocalDateTime criadoEm,
        String provincia,
        String distrito,
        long totalMarcacoes,
        BigDecimal totalInvestido,
        long faltas) {
}
