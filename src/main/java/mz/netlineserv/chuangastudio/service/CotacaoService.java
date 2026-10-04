package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.domain.enums.StatusCotacao;
import mz.netlineserv.chuangastudio.dto.request.CriarCotacaoRequest;
import mz.netlineserv.chuangastudio.dto.response.CotacaoResponse;

import mz.netlineserv.chuangastudio.common.PageResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface CotacaoService {

    CotacaoResponse criar(CriarCotacaoRequest request);

    PageResponse<CotacaoResponse> listarAdmin(StatusCotacao status, String busca, Pageable pageable);

    CotacaoResponse marcarComoRespondido(UUID id);

    CotacaoResponse arquivar(UUID id);
}
