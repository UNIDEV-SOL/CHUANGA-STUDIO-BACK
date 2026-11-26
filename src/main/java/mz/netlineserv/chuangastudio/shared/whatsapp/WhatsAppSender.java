package mz.netlineserv.chuangastudio.shared.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import mz.netlineserv.chuangastudio.shared.utils.Formatos;

/**
 * Envia a fatura em PDF pela WhatsApp Business Cloud API (Meta).
 *
 * Mensagens iniciadas pela empresa têm de usar um template aprovado no WhatsApp Manager,
 * com cabeçalho do tipo DOCUMENTO e três variáveis no corpo: {{1}} nome, {{2}} nº da fatura,
 * {{3}} valor. Sem WHATSAPP_TOKEN / WHATSAPP_PHONE_NUMBER_ID o envio é ignorado.
 *
 * Não há forma de saber antes se o número tem WhatsApp: a Meta aceita o pedido e, se o
 * número não tiver conta, a entrega falha depois (erro 131026, visível no WhatsApp Manager).
 */
@Slf4j
@Component
public class WhatsAppSender {

    private final RestClient restClient;

    @Value("${app.whatsapp.token:}")
    private String token;

    @Value("${app.whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${app.whatsapp.template-fatura:fatura_chuanga}")
    private String templateFatura;

    @Value("${app.whatsapp.idioma:pt_PT}")
    private String idioma;

    public WhatsAppSender(RestClient.Builder restClientBuilder,
                          @Value("${app.whatsapp.api-version:v21.0}") String apiVersion) {
        this.restClient = restClientBuilder.baseUrl("https://graph.facebook.com/" + apiVersion).build();
    }

    public boolean configurado() {
        return token != null && !token.isBlank() && phoneNumberId != null && !phoneNumberId.isBlank();
    }

    public void enviarFatura(String telefone, String nomeCliente, String numeroFatura, String total,
                             byte[] pdf, String nomeFicheiro) {
        String para = Formatos.telefoneInternacional(telefone);
        if (para == null) {
            return;
        }
        if (!configurado()) {
            log.info("WhatsApp não configurado: fatura {} não foi enviada para {}.", numeroFatura, para);
            return;
        }

        try {
            String mediaId = carregarPdf(pdf, nomeFicheiro);
            Map<String, Object> corpo = Map.of(
                    "messaging_product", "whatsapp",
                    "to", para,
                    "type", "template",
                    "template", Map.of(
                            "name", templateFatura,
                            "language", Map.of("code", idioma),
                            "components", List.of(
                                    Map.of("type", "header", "parameters", List.of(Map.of(
                                            "type", "document",
                                            "document", Map.of("id", mediaId, "filename", nomeFicheiro)))),
                                    Map.of("type", "body", "parameters", List.of(
                                            Map.of("type", "text", "text", nomeCliente),
                                            Map.of("type", "text", "text", numeroFatura),
                                            Map.of("type", "text", "text", total)))
                            )
                    )
            );
            try {
                enviar(corpo);
                log.info("Fatura {} enviada por WhatsApp (template) para {}.", numeroFatura, para);
            } catch (RestClientResponseException ex) {
                // Template ainda não aprovado/criado: tenta como documento direto. A Meta só aceita
                // se a cliente tiver escrito ao número nas últimas 24 h (janela de atendimento).
                log.warn("Template {} recusado ({}): a enviar a fatura {} como documento.", templateFatura,
                        ex.getStatusCode().value(), numeroFatura);
                enviar(Map.of(
                        "messaging_product", "whatsapp",
                        "to", para,
                        "type", "document",
                        "document", Map.of("id", mediaId, "filename", nomeFicheiro,
                                "caption", "Olá " + nomeCliente + "! Segue a sua fatura " + numeroFatura
                                        + " do Chuanga Studio (" + total + "). Obrigada pela visita 💅")));
                log.info("Fatura {} enviada por WhatsApp (documento) para {}.", numeroFatura, para);
            }
        } catch (RestClientResponseException ex) {
            log.error("WhatsApp recusou a fatura {} para {}: {} {}", numeroFatura, para,
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            log.error("Falha ao contactar o WhatsApp para a fatura {} ({}).", numeroFatura, para, ex);
        }
    }

    private void enviar(Map<String, Object> corpo) {
        restClient.post()
                .uri("/{id}/messages", phoneNumberId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .toBodilessEntity();
    }

    /** O PDF é carregado primeiro para a Meta; a mensagem referencia o id devolvido. */
    private String carregarPdf(byte[] pdf, String nomeFicheiro) {
        HttpHeaders cabecalhoFicheiro = new HttpHeaders();
        cabecalhoFicheiro.setContentType(MediaType.APPLICATION_PDF);
        ByteArrayResource ficheiro = new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return nomeFicheiro;
            }
        };

        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("messaging_product", "whatsapp");
        partes.add("type", "application/pdf");
        partes.add("file", new HttpEntity<>(ficheiro, cabecalhoFicheiro));

        Map<?, ?> resposta = restClient.post()
                .uri("/{id}/media", phoneNumberId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(partes)
                .retrieve()
                .body(Map.class);
        if (resposta == null || resposta.get("id") == null) {
            throw new RestClientException("A Meta não devolveu o id do PDF carregado.");
        }
        return resposta.get("id").toString();
    }
}
