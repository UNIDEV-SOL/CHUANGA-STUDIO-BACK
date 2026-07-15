package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.domain.enums.StatusCotacao;
import mz.netlineserv.chuangastudio.dto.request.CriarCotacaoRequest;
import mz.netlineserv.chuangastudio.dto.response.CotacaoResponse;

import java.util.List;
import java.util.UUID;

public interface CotacaoService {

    CotacaoResponse criar(CriarCotacaoRequest request);

    List<CotacaoResponse> listarAdmin(StatusCotacao status);

    CotacaoResponse marcarComoRespondido(UUID id);

    CotacaoResponse arquivar(UUID id);
}
