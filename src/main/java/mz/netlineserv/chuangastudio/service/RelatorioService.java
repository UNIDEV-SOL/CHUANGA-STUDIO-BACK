package mz.netlineserv.chuangastudio.service;

import mz.netlineserv.chuangastudio.dto.response.RelatorioCaixaResponse;

import java.time.LocalDate;

public interface RelatorioService {

    RelatorioCaixaResponse caixa(LocalDate inicio, LocalDate fim);
}
