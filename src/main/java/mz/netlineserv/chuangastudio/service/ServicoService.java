package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.domain.entity.Servico;
import mz.netlineserv.chuangastudio.dto.request.ServicoRequest;
import mz.netlineserv.chuangastudio.dto.response.ServicoResponse;

import java.util.List;
import java.util.UUID;

public interface ServicoService {

    List<ServicoResponse> listarPublico();

    List<ServicoResponse> listarTodosAdmin();

    ServicoResponse criar(ServicoRequest request);

    ServicoResponse atualizar(UUID id, ServicoRequest request);

    void eliminar(UUID id);

    Servico buscarPorId(UUID id);
}
