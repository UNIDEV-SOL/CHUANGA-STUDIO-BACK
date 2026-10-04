package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.domain.entity.Servico;
import mz.netlineserv.chuangastudio.dto.request.ServicoRequest;
import mz.netlineserv.chuangastudio.dto.response.ServicoResponse;

import mz.netlineserv.chuangastudio.common.PageResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface ServicoService {

    PageResponse<ServicoResponse> listarPublico(String busca, String categoria, Pageable pageable);

    PageResponse<ServicoResponse> listarAdmin(String busca, String categoria, Boolean ativo, Pageable pageable);

    ServicoResponse criar(ServicoRequest request);

    ServicoResponse atualizar(UUID id, ServicoRequest request);

    void eliminar(UUID id);

    Servico buscarPorId(UUID id);
}
