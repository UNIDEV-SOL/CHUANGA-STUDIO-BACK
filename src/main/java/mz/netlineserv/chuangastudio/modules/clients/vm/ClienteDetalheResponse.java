package mz.netlineserv.chuangastudio.modules.clients.vm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import mz.netlineserv.chuangastudio.modules.bookings.vm.MarcacaoResponse;
import mz.netlineserv.chuangastudio.modules.invoices.vm.FaturaResponse;

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
