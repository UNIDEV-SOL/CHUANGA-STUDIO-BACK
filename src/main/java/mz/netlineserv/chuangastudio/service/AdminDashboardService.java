package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.common.PageResponse;
import mz.netlineserv.chuangastudio.dto.response.ClienteResumoResponse;
import mz.netlineserv.chuangastudio.dto.response.EstatisticasAdminResponse;
import mz.netlineserv.chuangastudio.dto.response.MarcacaoResponse;
import org.springframework.data.domain.Pageable;

public interface AdminDashboardService {

    EstatisticasAdminResponse obterEstatisticas();

    PageResponse<MarcacaoResponse> proximasMarcacoes(Pageable pageable);

    PageResponse<MarcacaoResponse> atividadeRecente(Pageable pageable);

    PageResponse<ClienteResumoResponse> listarClientes(String busca, Long provinciaId, Long distritoId, Pageable pageable);
}
