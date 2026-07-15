package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.domain.enums.StatusMarcacao;
import mz.netlineserv.chuangastudio.dto.request.CriarMarcacaoRequest;
import mz.netlineserv.chuangastudio.dto.response.MarcacaoResponse;

import java.util.List;
import java.util.UUID;

public interface MarcacaoService {

    MarcacaoResponse criar(CriarMarcacaoRequest request, boolean autenticado);

    List<MarcacaoResponse> listarDoClienteOnline();

    List<MarcacaoResponse> listarTodasAdmin(StatusMarcacao status, String busca);

    MarcacaoResponse atualizarStatus(UUID id, StatusMarcacao novoStatus);

    MarcacaoResponse confirmarPagamento(UUID id);

    MarcacaoResponse cancelar(UUID id);

    void eliminar(UUID id);
}
