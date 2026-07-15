package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.dto.response.ClienteResumoResponse;
import mz.netlineserv.chuangastudio.dto.response.EstatisticasAdminResponse;
import mz.netlineserv.chuangastudio.dto.response.MarcacaoResponse;

import java.util.List;

public interface AdminDashboardService {

    EstatisticasAdminResponse obterEstatisticas();

    List<MarcacaoResponse> proximasMarcacoes();

    List<MarcacaoResponse> atividadeRecente();

    List<ClienteResumoResponse> listarClientesComResumo();
}
