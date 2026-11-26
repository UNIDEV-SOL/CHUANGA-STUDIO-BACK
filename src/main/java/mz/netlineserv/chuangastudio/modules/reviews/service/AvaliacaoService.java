package mz.netlineserv.chuangastudio.modules.reviews.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.shared.common.PageResponse;
import mz.netlineserv.chuangastudio.shared.exceptions.RecursoNaoEncontradoException;
import mz.netlineserv.chuangastudio.shared.exceptions.RegraNegocioException;
import mz.netlineserv.chuangastudio.modules.bookings.domain.Marcacao;
import mz.netlineserv.chuangastudio.modules.bookings.domain.MarcacaoServico;
import mz.netlineserv.chuangastudio.modules.notifications.domain.Notificacao;
import mz.netlineserv.chuangastudio.modules.notifications.domain.TipoNotificacao;
import mz.netlineserv.chuangastudio.shared.email.EmailComposer;
import mz.netlineserv.chuangastudio.shared.email.EmailSender;
import mz.netlineserv.chuangastudio.shared.utils.Formatos;
import mz.netlineserv.chuangastudio.modules.notifications.repository.NotificacaoRepository;
import mz.netlineserv.chuangastudio.shared.persistence.BaseSpecification;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Collectors;
import mz.netlineserv.chuangastudio.modules.reviews.domain.Avaliacao;
import mz.netlineserv.chuangastudio.modules.reviews.repository.AvaliacaoRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvaliacaoService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final AvaliacaoRepository avaliacaoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final EmailComposer emailComposer;
    private final EmailSender emailSender;

    public record AvaliacaoResponse(UUID id, String clienteNome, Short nota, String comentario, LocalDateTime respondidaEm,
                                    boolean publicada, LocalDate dataAtendimento, String servicos) {
        static AvaliacaoResponse de(Avaliacao a) {
            return new AvaliacaoResponse(a.getId(), a.getCliente().getNome(), a.getNota(), a.getComentario(), a.getRespondidaEm(),
                    a.isPublicada(), a.getMarcacao().getDataMarcacao(), nomesServicos(a.getMarcacao()));
        }

        /** No site só o primeiro nome e a inicial do apelido. */
        AvaliacaoResponse anonimizada() {
            String[] partes = clienteNome == null ? new String[0] : clienteNome.trim().split("\\s+");
            String nome = partes.length == 0 ? "Cliente" : partes.length == 1 ? partes[0] : partes[0] + " " + partes[partes.length - 1].charAt(0) + ".";
            return new AvaliacaoResponse(id, nome, nota, comentario, respondidaEm, publicada, dataAtendimento, servicos);
        }
    }

    public record PedidoPublico(String primeiroNome, LocalDate data, String servicos, boolean respondida, Short nota) {
    }

    public record Resumo(BigDecimal media, long total) {
    }

    public record ResponderRequest(Short nota, String comentario) {
    }

    /** Cria o pedido (uma vez por marcação) e envia o email com o link. */
    @Transactional
    public void pedirAvaliacao(Marcacao marcacao) {
        if (avaliacaoRepository.existsByMarcacaoId(marcacao.getId())) {
            return;
        }
        Avaliacao a = new Avaliacao();
        a.setMarcacao(marcacao);
        a.setCliente(marcacao.getCliente());
        byte[] bytes = new byte[24];
        ALEATORIO.nextBytes(bytes);
        a.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        avaliacaoRepository.save(a);
        String email = marcacao.getCliente().getEmail();
        if (email != null && !email.isBlank()) {
            emailSender.enviar(emailComposer.comporPedidoAvaliacao(marcacao, a.getToken()));
        }
    }

    @Transactional(readOnly = true)
    public PedidoPublico pedido(String token) {
        Avaliacao a = porToken(token);
        return new PedidoPublico(Formatos.primeiroNome(a.getCliente().getNome()), a.getMarcacao().getDataMarcacao(),
                nomesServicos(a.getMarcacao()), a.getRespondidaEm() != null, a.getNota());
    }

    @Transactional
    public void responder(String token, ResponderRequest r) {
        Avaliacao a = porToken(token);
        if (a.getRespondidaEm() != null) {
            throw new RegraNegocioException("Esta avaliação já foi enviada. Obrigada!");
        }
        if (r.nota() == null || r.nota() < 1 || r.nota() > 5) {
            throw new RegraNegocioException("Escolha de 1 a 5 estrelas.");
        }
        String comentario = r.comentario() == null || r.comentario().isBlank() ? null : r.comentario().trim();
        if (comentario != null && comentario.length() > 1000) {
            throw new RegraNegocioException("O comentário pode ter até 1000 caracteres.");
        }
        a.setNota(r.nota());
        a.setComentario(comentario);
        a.setRespondidaEm(LocalDateTime.now());
        avaliacaoRepository.save(a);

        Notificacao n = new Notificacao();
        n.setTipo(TipoNotificacao.AVALIACAO_NOVA);
        n.setTitulo("Nova avaliação " + "★".repeat(r.nota()) + " · " + a.getCliente().getNome());
        n.setMensagem(comentario != null ? (comentario.length() > 120 ? comentario.substring(0, 119) + "…" : comentario) : "Sem comentário");
        n.setLink("/admin/avaliacoes");
        n.setReferenciaId(a.getId());
        notificacaoRepository.save(n);
    }

    @Transactional(readOnly = true)
    public PageResponse<AvaliacaoResponse> publicas(Pageable pageable) {
        var spec = BaseSpecification.of(Avaliacao.class).equals("publicada", true).build();
        return PageResponse.from(avaliacaoRepository.findAll(spec, pageable).map(a -> AvaliacaoResponse.de(a).anonimizada()));
    }

    @Transactional(readOnly = true)
    public Resumo resumo() {
        Object[] r = avaliacaoRepository.resumoPublicadas().get(0);
        BigDecimal media = new BigDecimal(r[0].toString()).setScale(1, RoundingMode.HALF_UP);
        return new Resumo(media, ((Number) r[1]).longValue());
    }

    @Transactional(readOnly = true)
    public PageResponse<AvaliacaoResponse> admin(Short nota, Boolean publicada, Boolean respondida, Pageable pageable) {
        var spec = BaseSpecification.of(Avaliacao.class)
                .equals("nota", nota)
                .equals("publicada", publicada)
                .and(respondida == null ? null : (root, q, cb) -> respondida ? cb.isNotNull(root.get("respondidaEm")) : cb.isNull(root.get("respondidaEm")))
                .build();
        return PageResponse.from(avaliacaoRepository.findAll(spec, pageable).map(AvaliacaoResponse::de));
    }

    @Transactional
    public AvaliacaoResponse publicar(UUID id, boolean publicar) {
        Avaliacao a = avaliacaoRepository.findById(id).filter(x -> !x.isDeletado())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Avaliação não encontrada."));
        if (publicar && a.getRespondidaEm() == null) {
            throw new RegraNegocioException("A cliente ainda não respondeu.");
        }
        a.setPublicada(publicar);
        return AvaliacaoResponse.de(avaliacaoRepository.save(a));
    }

    private Avaliacao porToken(String token) {
        return avaliacaoRepository.findByTokenAndDeletadoFalse(token)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Link de avaliação inválido."));
    }

    private static String nomesServicos(Marcacao m) {
        return m.getServicos().stream().map(MarcacaoServico::getNomeServico).collect(Collectors.joining(", "));
    }
}
