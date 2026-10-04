package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.common.PageResponse;
import mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao;
import mz.netlineserv.chuangastudio.dto.request.FiltroMarcacoes;
import org.springframework.data.domain.Pageable;
import mz.netlineserv.chuangastudio.dto.request.CriarMarcacaoRequest;
import mz.netlineserv.chuangastudio.dto.response.MarcacaoResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface MarcacaoService {

    MarcacaoResponse criar(CriarMarcacaoRequest request, boolean autenticado);

    PageResponse<MarcacaoResponse> listarDoClienteOnline(Pageable pageable);

    PageResponse<MarcacaoResponse> listarAdmin(FiltroMarcacoes filtro, Pageable pageable);

    MarcacaoResponse obterAdmin(UUID id);

    MarcacaoResponse atualizarStatus(UUID id, StatusMarcacao novoStatus);

    MarcacaoResponse confirmarPagamento(UUID id);

    MarcacaoResponse cancelarDoClienteOnline(UUID id);

    /** Pela cliente: só marcações ativas e com antecedência mínima (app.marcacoes.antecedencia-remarcar-horas). */
    MarcacaoResponse remarcarDoClienteOnline(UUID id, LocalDate data, LocalTime hora);

    /** Pelo estúdio: muda quem faz o atendimento (nulo = sem profissional). */
    MarcacaoResponse atribuirProfissional(UUID id, UUID profissionalId);

    /** Pelo estúdio: sem antecedência mínima. */
    MarcacaoResponse remarcarAdmin(UUID id, LocalDate data, LocalTime hora);

    void eliminar(UUID id);
}
