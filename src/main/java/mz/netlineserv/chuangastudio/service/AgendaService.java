package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.dto.request.AtualizarHorarioRequest;
import mz.netlineserv.chuangastudio.dto.request.DiaFechadoRequest;
import mz.netlineserv.chuangastudio.dto.response.ConfiguracaoHorarioResponse;
import mz.netlineserv.chuangastudio.dto.response.DisponibilidadeResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import mz.netlineserv.chuangastudio.domain.entity.Profissional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Horário de funcionamento, dias fechados e horas livres para marcações. */
public interface AgendaService {

    /**
     * ignorarMarcacaoId: ao remarcar, a própria marcação não conta como ocupação.
     * profissionalId: só horas em que essa profissional está livre (nulo = qualquer uma).
     */
    DisponibilidadeResponse disponibilidade(LocalDate data, List<UUID> servicosIds, UUID ignorarMarcacaoId, UUID profissionalId);

    /** Lança RegraNegocioException com o motivo se não for possível marcar nesse dia/hora (com essa profissional, se indicada). */
    void validarMarcacao(LocalDate data, LocalTime hora, int duracaoMinutos, UUID ignorarMarcacaoId, UUID profissionalId);

    /** Primeira profissional ativa livre nesse intervalo, pela ordem da equipa. Vazio se não há equipa ou ninguém livre. */
    Optional<Profissional> escolherProfissional(LocalDate data, LocalTime hora, int duracaoMinutos, UUID ignorarMarcacaoId);

    ConfiguracaoHorarioResponse configuracao();

    ConfiguracaoHorarioResponse atualizarHorario(AtualizarHorarioRequest request);

    ConfiguracaoHorarioResponse adicionarDiaFechado(DiaFechadoRequest request);

    ConfiguracaoHorarioResponse removerDiaFechado(UUID id);
}
