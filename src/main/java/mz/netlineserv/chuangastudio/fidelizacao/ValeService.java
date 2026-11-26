package mz.netlineserv.chuangastudio.fidelizacao;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import mz.netlineserv.chuangastudio.common.PageResponse;
import mz.netlineserv.chuangastudio.common.exception.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import mz.netlineserv.chuangastudio.repository.spec.BaseSpecification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** Vales-oferta: a equipa emite depois de receber o pagamento; usam-se no fecho de conta até o saldo acabar. */
@Service
@RequiredArgsConstructor
public class ValeService {

    // Sem 0/O/1/I para não haver enganos ao ditar o código.
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final ZoneId FUSO = ZoneId.of("Africa/Maputo");

    private final ValeRepository valeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public record CriarValeRequest(
            @DecimalMax(value = "1000000", message = "O valor máximo de um vale é 1 000 000 MZN") BigDecimal valor,
            @Size(max = 150, message = "O nome de quem compra pode ter até 150 caracteres") String compradorNome,
            @Size(max = 150, message = "O contacto de quem compra pode ter até 150 caracteres") String compradorContacto,
            @Size(max = 150, message = "O nome da beneficiária pode ter até 150 caracteres") String beneficiarioNome,
            @Email(message = "Email da beneficiária inválido") @Size(max = 180, message = "O email pode ter até 180 caracteres") String beneficiarioEmail,
            @Size(max = 400, message = "A mensagem pode ter até 400 caracteres") String mensagem,
            LocalDate validoAte) {
    }

    public record ValeResponse(UUID id, String codigo, BigDecimal valorInicial, BigDecimal saldo, String compradorNome,
                               String compradorContacto, String beneficiarioNome, String beneficiarioEmail, String mensagem,
                               LocalDate validoAte, String estado, boolean expirado, LocalDateTime criadoEm) {
        static ValeResponse de(Vale v) {
            boolean expirado = v.getValidoAte() != null && v.getValidoAte().isBefore(LocalDate.now(FUSO));
            return new ValeResponse(v.getId(), v.getCodigo(), v.getValorInicial(), v.getSaldo(), v.getCompradorNome(),
                    v.getCompradorContacto(), v.getBeneficiarioNome(), v.getBeneficiarioEmail(), v.getMensagem(),
                    v.getValidoAte(), v.getEstado().name(), expirado, v.getCriadoEm());
        }
    }

    /** O que o site mostra a quem tem o código (sem dados de quem comprou). */
    public record SaldoPublico(String codigo, BigDecimal saldo, LocalDate validoAte, String estado, boolean expirado) {
    }

    /** Publicado ao emitir um vale com email da beneficiária (envia o vale por email). */
    public record ValeEmitidoEvent(UUID valeId) {
    }

    @Transactional
    public ValeResponse criar(CriarValeRequest r) {
        if (r.valor() == null || r.valor().compareTo(new BigDecimal("50")) < 0) {
            throw new RegraNegocioException("O valor mínimo de um vale é 50 MZN.");
        }
        if (r.validoAte() != null && r.validoAte().isBefore(LocalDate.now(FUSO))) {
            throw new RegraNegocioException("A validade tem de ser uma data futura.");
        }
        Vale v = new Vale();
        v.setCodigo(novoCodigo());
        BigDecimal valor = r.valor().setScale(2, RoundingMode.HALF_UP);
        v.setValorInicial(valor);
        v.setSaldo(valor);
        v.setCompradorNome(texto(r.compradorNome()));
        v.setCompradorContacto(texto(r.compradorContacto()));
        v.setBeneficiarioNome(texto(r.beneficiarioNome()));
        v.setBeneficiarioEmail(texto(r.beneficiarioEmail()));
        v.setMensagem(texto(r.mensagem()));
        v.setValidoAte(r.validoAte() != null ? r.validoAte() : LocalDate.now(FUSO).plusYears(1));
        Vale salvo = valeRepository.save(v);
        if (salvo.getBeneficiarioEmail() != null) {
            eventPublisher.publishEvent(new ValeEmitidoEvent(salvo.getId()));
        }
        return ValeResponse.de(salvo);
    }

    @Transactional(readOnly = true)
    public PageResponse<ValeResponse> listar(String busca, Vale.Estado estado, Pageable pageable) {
        var spec = BaseSpecification.of(Vale.class)
                .likeAny(busca, "codigo", "compradorNome", "beneficiarioNome", "beneficiarioEmail")
                .equals("estado", estado)
                .build();
        return PageResponse.from(valeRepository.findAll(spec, pageable).map(ValeResponse::de));
    }

    @Transactional
    public ValeResponse anular(UUID id) {
        Vale v = valeRepository.findById(id).filter(x -> !x.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vale não encontrado."));
        v.setEstado(Vale.Estado.ANULADO);
        return ValeResponse.de(valeRepository.save(v));
    }

    @Transactional(readOnly = true)
    public SaldoPublico saldo(String codigo) {
        Vale v = porCodigo(codigo);
        return new SaldoPublico(v.getCodigo(), v.getSaldo(), v.getValidoAte(), v.getEstado().name(),
                v.getValidoAte() != null && v.getValidoAte().isBefore(LocalDate.now(FUSO)));
    }

    /** Valida para uso no fecho de conta. */
    @Transactional(readOnly = true)
    public Vale validarParaUso(String codigo) {
        Vale v = porCodigo(codigo);
        if (v.getEstado() != Vale.Estado.ATIVO || v.getSaldo().signum() <= 0) {
            throw new RegraNegocioException("Este vale já não tem saldo ou foi anulado.");
        }
        if (v.getValidoAte() != null && v.getValidoAte().isBefore(LocalDate.now(FUSO))) {
            throw new RegraNegocioException("Este vale expirou a " + v.getValidoAte() + ".");
        }
        return v;
    }

    /** Desconta do saldo (chamado dentro da transação da fatura). */
    public void usar(Vale v, BigDecimal valor) {
        v.setSaldo(v.getSaldo().subtract(valor));
        if (v.getSaldo().signum() <= 0) {
            v.setSaldo(BigDecimal.ZERO);
            v.setEstado(Vale.Estado.ESGOTADO);
        }
        valeRepository.save(v);
    }

    /** Devolve o valor ao vale (fatura anulada). */
    public void devolver(Vale v, BigDecimal valor) {
        v.setSaldo(v.getSaldo().add(valor));
        if (v.getEstado() == Vale.Estado.ESGOTADO) {
            v.setEstado(Vale.Estado.ATIVO);
        }
        valeRepository.save(v);
    }

    private Vale porCodigo(String codigo) {
        String limpo = codigo == null ? "" : codigo.trim().toUpperCase();
        return valeRepository.findByCodigoIgnoreCaseAndDeletadoFalse(limpo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vale não encontrado. Confirme o código."));
    }

    private String novoCodigo() {
        String codigo;
        do {
            StringBuilder sb = new StringBuilder("CHG-");
            for (int i = 0; i < 8; i++) {
                if (i == 4) sb.append('-');
                sb.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
            }
            codigo = sb.toString();
        } while (valeRepository.findByCodigoIgnoreCaseAndDeletadoFalse(codigo).isPresent());
        return codigo;
    }

    private static String texto(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
