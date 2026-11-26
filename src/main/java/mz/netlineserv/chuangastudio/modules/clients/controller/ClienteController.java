package mz.netlineserv.chuangastudio.modules.clients.controller;

import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.shared.common.ApiResponse;
import mz.netlineserv.chuangastudio.shared.exceptions.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.modules.invoices.domain.Fatura;
import mz.netlineserv.chuangastudio.modules.bookings.domain.Marcacao;
import mz.netlineserv.chuangastudio.modules.users.domain.Usuario;
import mz.netlineserv.chuangastudio.modules.users.domain.PapelUsuario;
import mz.netlineserv.chuangastudio.modules.invoices.domain.StatusFatura;
import mz.netlineserv.chuangastudio.modules.bookings.domain.StatusMarcacao;
import mz.netlineserv.chuangastudio.modules.clients.vm.ClienteDetalheResponse;
import mz.netlineserv.chuangastudio.modules.invoices.vm.FaturaResponse;
import mz.netlineserv.chuangastudio.modules.bookings.vm.MarcacaoResponse;
import mz.netlineserv.chuangastudio.shared.config.DadosEstudio;
import mz.netlineserv.chuangastudio.modules.invoices.repository.FaturaRepository;
import mz.netlineserv.chuangastudio.modules.bookings.repository.MarcacaoRepository;
import mz.netlineserv.chuangastudio.modules.users.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Ficha da cliente no painel (só leitura + notas internas). */
@RestController
@Validated
@RequiredArgsConstructor
public class ClienteController {

    private final UsuarioRepository usuarioRepository;
    private final MarcacaoRepository marcacaoRepository;
    private final FaturaRepository faturaRepository;
    private final DadosEstudio dadosEstudio;

    public record NotasRequest(@Size(max = 2000, message = "As notas não podem ter mais de 2000 caracteres") String notas) {
    }

    @GetMapping("/admin/clientes/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<ClienteDetalheResponse>> ficha(@PathVariable UUID id) {
        Usuario cliente = buscarCliente(id);
        List<Marcacao> marcacoes = marcacaoRepository.buscarDoClienteComDetalhes(id);
        List<Fatura> faturas = faturaRepository.buscarDoClienteComDetalhes(id);

        BigDecimal faturado = faturas.stream()
                .filter(f -> f.getStatus() != StatusFatura.ANULADA)
                .map(f -> f.getSubtotal().subtract(f.getDesconto()).subtract(f.getDescontoFidelizacao()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate ultimaVisita = marcacoes.stream()
                .filter(m -> m.getStatus() == StatusMarcacao.CONCLUIDA)
                .map(Marcacao::getDataMarcacao)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return ResponseEntity.ok(ApiResponse.sucesso(new ClienteDetalheResponse(
                cliente.getId(), cliente.getNome(), cliente.getEmail(), cliente.getTelefone(), cliente.getCriadoEm(),
                cliente.getNotasInternas(),
                marcacoes.size(),
                contar(marcacoes, StatusMarcacao.CONCLUIDA),
                contar(marcacoes, StatusMarcacao.CANCELADA),
                contar(marcacoes, StatusMarcacao.NAO_COMPARECEU),
                faturado,
                ultimaVisita,
                marcacoes.stream().map(MarcacaoResponse::deEntidade).toList(),
                faturas.stream().map(f -> FaturaResponse.deEntidade(f, dadosEstudio.urlPdfFatura(f.getTokenPublico()))).toList())));
    }

    @PatchMapping("/admin/clientes/{id}/notas")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> guardarNotas(@PathVariable UUID id, @RequestBody @jakarta.validation.Valid NotasRequest request) {
        Usuario cliente = buscarCliente(id);
        String notas = request.notas() == null || request.notas().isBlank() ? null : request.notas().trim();
        cliente.setNotasInternas(notas);
        usuarioRepository.save(cliente);
        return ResponseEntity.ok(ApiResponse.sucesso("Notas guardadas.", null));
    }

    private Usuario buscarCliente(UUID id) {
        return usuarioRepository.findById(id)
                .filter(u -> !u.isDeletado() && u.getPapel() == PapelUsuario.CLIENTE)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrada."));
    }

    private static long contar(List<Marcacao> marcacoes, StatusMarcacao status) {
        return marcacoes.stream().filter(m -> m.getStatus() == status).count();
    }
}
