package mz.netlineserv.chuangastudio.notificacao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.domain.entity.Marcacao;
import mz.netlineserv.chuangastudio.domain.entity.MarcacaoServico;
import mz.netlineserv.chuangastudio.domain.entity.Notificacao;
import mz.netlineserv.chuangastudio.domain.enums.StatusFatura;
import mz.netlineserv.chuangastudio.domain.enums.TipoNotificacao;
import mz.netlineserv.chuangastudio.repository.CotacaoRepository;
import mz.netlineserv.chuangastudio.repository.FaturaRepository;
import mz.netlineserv.chuangastudio.repository.MarcacaoRepository;
import mz.netlineserv.chuangastudio.repository.NotificacaoRepository;
import mz.netlineserv.chuangastudio.repository.UsuarioRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;
import java.util.stream.Collectors;

import static mz.netlineserv.chuangastudio.notificacao.Formatos.*;

/**
 * Notificações do sistema (sino do painel): os mesmos eventos que geram emails geram também
 * um aviso para a equipa, depois do commit. Uma falha aqui nunca afeta a operação original.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoSistemaListener {

    private final NotificacaoRepository notificacaoRepository;
    private final MarcacaoRepository marcacaoRepository;
    private final CotacaoRepository cotacaoRepository;
    private final FaturaRepository faturaRepository;
    private final UsuarioRepository usuarioRepository;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoMudarMarcacao(NotificacaoMarcacaoEvent evento) {
        if (evento.tipo() == NotificacaoMarcacaoEvent.Tipo.LEMBRETE) {
            return;
        }
        try {
            marcacaoRepository.buscarPorIdComDetalhes(evento.marcacaoId()).ifPresent(m -> {
                String cliente = m.getCliente().getNome();
                String quando = m.getDataMarcacao().format(DATA_NUMERICA) + " às " + m.getHoraMarcacao().format(HORA);
                // avisarEstudio = a ação partiu da cliente (no site), não da equipa.
                boolean pelaCliente = evento.avisarEstudio();
                switch (evento.tipo()) {
                    case RECEBIDA -> criar(TipoNotificacao.MARCACAO_NOVA, "Nova marcação · " + cliente,
                            quando + " · " + servicos(m) + " · " + dinheiro(m.getValorTotal()), m);
                    case CONFIRMADA -> criar(TipoNotificacao.MARCACAO_CONFIRMADA, "Marcação confirmada · " + cliente, quando, m);
                    case CANCELADA -> criar(TipoNotificacao.MARCACAO_CANCELADA,
                            (pelaCliente ? "Cancelada pela cliente · " : "Marcação cancelada · ") + cliente,
                            quando + (pelaCliente ? " · o horário ficou livre" : ""), m);
                    case REMARCADA -> criar(TipoNotificacao.MARCACAO_REMARCADA,
                            (pelaCliente ? "Remarcada pela cliente · " : "Marcação remarcada · ") + cliente,
                            "Nova data: " + quando, m);
                    default -> { }
                }
            });
        } catch (Exception ex) {
            log.error("Falha ao criar notificação da marcação {}", evento.marcacaoId(), ex);
        }
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCriarCotacao(NotificacaoCotacaoEvent evento) {
        try {
            cotacaoRepository.findById(evento.cotacaoId()).ifPresent(c -> salvar(TipoNotificacao.COTACAO_NOVA,
                    "Novo pedido de cotação · " + c.getNome(),
                    dinheiro(c.getTotalEstimado()) + (c.getTelefone() != null ? " · " + c.getTelefone() : ""),
                    "/admin/cotacoes", c.getId()));
        } catch (Exception ex) {
            log.error("Falha ao criar notificação da cotação {}", evento.cotacaoId(), ex);
        }
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoMudarFatura(NotificacaoFaturaEvent evento) {
        if (!evento.avisarEstudio()) {
            return; // reenvio manual: não é uma transação nova
        }
        try {
            faturaRepository.buscarPorIdComDetalhes(evento.faturaId()).ifPresent(f -> {
                boolean paga = f.getStatus() == StatusFatura.PAGA;
                String cliente = f.getMarcacao().getCliente().getNome();
                salvar(paga ? TipoNotificacao.FATURA_PAGA : TipoNotificacao.FATURA_EMITIDA,
                        (paga ? "Fatura paga · " : "Fatura emitida · ") + f.getNumero(),
                        cliente + " · " + dinheiro(f.getTotalAPagar())
                                + (paga && f.getFormaPagamento() != null ? " · " + f.getFormaPagamento().rotulo() : ""),
                        "/admin/faturas?id=" + f.getId(), f.getId());
            });
        } catch (Exception ex) {
            log.error("Falha ao criar notificação da fatura {}", evento.faturaId(), ex);
        }
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCriarCliente(NovoClienteEvent evento) {
        try {
            usuarioRepository.findById(evento.usuarioId()).ifPresent(u -> salvar(TipoNotificacao.CLIENTE_NOVO,
                    "Nova cliente registada · " + u.getNome(),
                    u.getTelefone() != null ? u.getTelefone() : u.getEmail(),
                    "/admin/clientes?id=" + u.getId(), u.getId()));
        } catch (Exception ex) {
            log.error("Falha ao criar notificação de cliente {}", evento.usuarioId(), ex);
        }
    }

    private void criar(TipoNotificacao tipo, String titulo, String mensagem, Marcacao m) {
        salvar(tipo, titulo, mensagem, "/admin/marcacoes?id=" + m.getId(), m.getId());
    }

    private void salvar(TipoNotificacao tipo, String titulo, String mensagem, String link, UUID referencia) {
        Notificacao n = new Notificacao();
        n.setTipo(tipo);
        n.setTitulo(cortar(titulo, 160));
        n.setMensagem(cortar(mensagem, 400));
        n.setLink(link);
        n.setReferenciaId(referencia);
        notificacaoRepository.save(n);
    }

    private static String servicos(Marcacao m) {
        return m.getServicos().stream().map(MarcacaoServico::getNomeServico).collect(Collectors.joining(", "));
    }

    private static String cortar(String texto, int max) {
        return texto == null || texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }
}
