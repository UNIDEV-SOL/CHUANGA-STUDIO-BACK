package mz.netlineserv.chuangastudio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Ficha da cliente para a equipa: contactos, notas internas, números e histórico. */
public record ClienteDetalheResponse(
        UUID id,
        String nome,
        String email,
        String telefone,
        LocalDateTime criadoEm,
        String notasInternas,
        long totalMarcacoes,
        long concluidas,
        long canceladas,
        long faltas,
        BigDecimal totalFaturado,
        LocalDate ultimaVisita,
        List<MarcacaoResponse> marcacoes,
        List<FaturaResponse> faturas) {
}
