package mz.netlineserv.chuangastudio.shared.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Envia emails pela API REST do Resend (https://resend.com/docs/api-reference/emails/send-email).
 * Sem RESEND_API_KEY, o envio é ignorado e fica apenas registado no log.
 */
@Slf4j
@Component
public class EmailSender {

    private static final DateTimeFormatter CARIMBO_FICHEIRO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final RestClient restClient;

    @Value("${app.email.resend-api-key:}")
    private String apiKey;

    @Value("${app.email.remetente}")
    private String remetente;

    @Value("${app.email.responder-para:}")
    private String responderPara;

    @Value("${app.email.pasta-pre-visualizacao:}")
    private String pastaPreVisualizacao;

    public EmailSender(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("https://api.resend.com").build();
    }

    public void enviar(EmailMensagem mensagem) {
        guardarPreVisualizacao(mensagem);

        if (apiKey == null || apiKey.isBlank()) {
            log.info("RESEND_API_KEY não definida: email \"{}\" para {} não foi enviado.",
                    mensagem.assunto(), mensagem.para());
            return;
        }

        // "para" pode ter vários endereços separados por vírgula (ex.: EMAIL_ESTUDIO com a equipa toda).
        // Um envio por endereço: se o Resend recusar um, os outros recebem na mesma.
        Arrays.stream(mensagem.para().split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .forEach(email -> enviarPara(email, mensagem));
    }

    private void enviarPara(String para, EmailMensagem mensagem) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("from", remetente);
        corpo.put("to", List.of(para));
        corpo.put("subject", mensagem.assunto());
        corpo.put("html", mensagem.html());
        String replyTo = mensagem.responderPara() != null && !mensagem.responderPara().isBlank()
                ? mensagem.responderPara()
                : responderPara;
        if (replyTo != null && !replyTo.isBlank()) {
            corpo.put("reply_to", replyTo);
        }
        if (mensagem.anexos() != null && !mensagem.anexos().isEmpty()) {
            corpo.put("attachments", mensagem.anexos().stream()
                    .map(a -> Map.of("filename", a.nome(), "content", Base64.getEncoder().encodeToString(a.conteudo())))
                    .toList());
        }

        try {
            restClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Email \"{}\" enviado para {}.", mensagem.assunto(), para);
        } catch (RestClientResponseException ex) {
            log.error("Resend recusou o email \"{}\" para {}: {} {}", mensagem.assunto(), para,
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            log.error("Falha ao contactar o Resend para o email \"{}\" para {}.", mensagem.assunto(), para, ex);
        }
    }

    private void guardarPreVisualizacao(EmailMensagem mensagem) {
        if (pastaPreVisualizacao == null || pastaPreVisualizacao.isBlank()) {
            return;
        }
        try {
            Path pasta = Files.createDirectories(Path.of(pastaPreVisualizacao));
            String slug = Normalizer.normalize(mensagem.assunto(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
            String prefixo = LocalDateTime.now().format(CARIMBO_FICHEIRO) + "-" + slug;
            Path ficheiro = pasta.resolve(prefixo + ".html");
            Files.writeString(ficheiro, mensagem.html());
            if (mensagem.anexos() != null) {
                for (EmailMensagem.Anexo anexo : mensagem.anexos()) {
                    Files.write(pasta.resolve(prefixo + "-" + anexo.nome()), anexo.conteudo());
                }
            }
            log.debug("Pré-visualização do email guardada em {}", ficheiro);
        } catch (IOException ex) {
            log.warn("Não foi possível guardar a pré-visualização do email em {}", pastaPreVisualizacao, ex);
        }
    }
}
