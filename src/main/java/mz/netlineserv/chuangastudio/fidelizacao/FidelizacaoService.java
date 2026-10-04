package mz.netlineserv.chuangastudio.fidelizacao;

import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import mz.netlineserv.chuangastudio.repository.FaturaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cartão de carimbos: cada atendimento faturado é um carimbo; a cada N carimbos a cliente ganha
 * uma recompensa (desconto em MZN) que a equipa aplica num próximo fecho de conta.
 * Tudo se calcula a partir das faturas, por isso anular uma fatura devolve o carimbo/recompensa.
 */
@Service
@RequiredArgsConstructor
public class FidelizacaoService {

    private final FidelizacaoConfigRepository configRepository;
    private final FaturaRepository faturaRepository;

    public record ConfigDto(boolean ativo, int visitasPorRecompensa, BigDecimal valorRecompensa) {
    }

    public record EstadoCliente(boolean ativo, long visitas, int visitasPorRecompensa, long carimbos,
                                long recompensasDisponiveis, BigDecimal valorRecompensa) {
    }

    @Transactional(readOnly = true)
    public ConfigDto config() {
        FidelizacaoConfig c = carregar();
        return new ConfigDto(c.isAtivo(), c.getVisitasPorRecompensa(), c.getValorRecompensa());
    }

    @Transactional
    public ConfigDto atualizar(ConfigDto dto) {
        if (dto.visitasPorRecompensa() < 2 || dto.visitasPorRecompensa() > 50) {
            throw new RegraNegocioException("O nº de visitas por recompensa tem de estar entre 2 e 50.");
        }
        if (dto.valorRecompensa() == null || dto.valorRecompensa().signum() <= 0) {
            throw new RegraNegocioException("Indique o valor da recompensa.");
        }
        FidelizacaoConfig c = carregar();
        c.setAtivo(dto.ativo());
        c.setVisitasPorRecompensa(dto.visitasPorRecompensa());
        c.setValorRecompensa(dto.valorRecompensa());
        c.setAtualizadoEm(LocalDateTime.now());
        configRepository.save(c);
        return config();
    }

    @Transactional(readOnly = true)
    public EstadoCliente estado(UUID clienteId) {
        FidelizacaoConfig c = carregar();
        long visitas = faturaRepository.contarVisitas(clienteId);
        long ganhas = visitas / c.getVisitasPorRecompensa();
        long usadas = faturaRepository.contarRecompensasUsadas(clienteId);
        return new EstadoCliente(c.isAtivo(), visitas, c.getVisitasPorRecompensa(), visitas % c.getVisitasPorRecompensa(),
                c.isAtivo() ? Math.max(0, ganhas - usadas) : 0, c.getValorRecompensa());
    }

    /** Valor a descontar no fecho de conta (lança erro se a cliente não tiver recompensa). */
    @Transactional(readOnly = true)
    public BigDecimal valorParaAplicar(UUID clienteId) {
        EstadoCliente e = estado(clienteId);
        if (!e.ativo() || e.recompensasDisponiveis() <= 0) {
            throw new RegraNegocioException("Esta cliente não tem recompensa de fidelização disponível.");
        }
        return e.valorRecompensa();
    }

    private FidelizacaoConfig carregar() {
        return configRepository.findById((short) 1).orElseGet(FidelizacaoConfig::new);
    }
}
