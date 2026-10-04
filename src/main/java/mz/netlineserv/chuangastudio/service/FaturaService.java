package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.domain.enums.StatusFatura;
import mz.netlineserv.chuangastudio.dto.request.EmitirFaturaRequest;
import mz.netlineserv.chuangastudio.dto.request.RegistarPagamentoFaturaRequest;
import mz.netlineserv.chuangastudio.dto.response.FaturaResponse;

import mz.netlineserv.chuangastudio.common.PageResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface FaturaService {

    record Pdf(String nomeFicheiro, byte[] conteudo) {
    }

    FaturaResponse emitir(UUID marcacaoId, EmitirFaturaRequest request);

    FaturaResponse registarPagamento(UUID id, RegistarPagamentoFaturaRequest request);

    FaturaResponse anular(UUID id);

    void reenviar(UUID id, String email);

    PageResponse<FaturaResponse> listarAdmin(StatusFatura status, String busca, UUID clienteId,
                                            java.time.LocalDate dataInicio, java.time.LocalDate dataFim, Pageable pageable);

    PageResponse<FaturaResponse> listarDoClienteOnline(Pageable pageable);

    FaturaResponse obterAdmin(UUID id);

    Pdf pdfAdmin(UUID id);

    Pdf pdfDoClienteOnline(UUID id);

    Pdf pdfPublico(String token);
}
