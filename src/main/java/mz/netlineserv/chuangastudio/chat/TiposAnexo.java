package mz.netlineserv.chuangastudio.chat;

import mz.netlineserv.chuangastudio.common.exception.RegraNegocioException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tipos de ficheiro aceites no chat. Lista fechada de propósito: HTML, SVG ou XML enviados como
 * "anexo" e abertos no domínio da API correriam JavaScript (XSS armazenado).
 */
final class TiposAnexo {

    static final Set<String> IMAGENS = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    static final Set<String> AUDIO = Set.of("audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4", "audio/aac",
            "audio/wav", "audio/x-wav", "audio/x-m4a", "audio/m4a");

    /** Documentos: tipo → descrição curta para a mensagem de erro. */
    static final Map<String, String> DOCUMENTOS = Map.ofEntries(
            Map.entry("application/pdf", "PDF"),
            Map.entry("application/msword", "Word"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word"),
            Map.entry("application/vnd.ms-excel", "Excel"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel"),
            Map.entry("application/vnd.ms-powerpoint", "PowerPoint"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint"),
            Map.entry("application/vnd.oasis.opendocument.text", "ODT"),
            Map.entry("application/vnd.oasis.opendocument.spreadsheet", "ODS"),
            Map.entry("text/plain", "texto"),
            Map.entry("text/csv", "CSV"));

    private TiposAnexo() {
    }

    /** "Audio/WebM; codecs=opus" → "audio/webm". */
    static String normalizar(String contentType) {
        if (contentType == null || contentType.isBlank()) return null;
        int pv = contentType.indexOf(';');
        return (pv >= 0 ? contentType.substring(0, pv) : contentType).trim().toLowerCase(Locale.ROOT);
    }

    static String exigirImagem(String contentType) {
        String tipo = normalizar(contentType);
        if (tipo == null || !IMAGENS.contains(tipo)) {
            throw new RegraNegocioException("Formato de imagem não aceite (" + (tipo == null ? "tipo desconhecido" : tipo)
                    + "). Envie JPG, PNG, WebP ou GIF.");
        }
        return tipo;
    }

    static String exigirAudio(String contentType) {
        String tipo = normalizar(contentType);
        if (tipo == null || !AUDIO.contains(tipo)) {
            throw new RegraNegocioException("Formato de áudio não aceite (" + (tipo == null ? "tipo desconhecido" : tipo)
                    + "). Use WebM, OGG, MP3, M4A/AAC ou WAV.");
        }
        return tipo;
    }

    /** Documento, ou também foto/áudio enviados pelo botão de anexar. */
    static String exigirFicheiro(String contentType, String nome) {
        String tipo = normalizar(contentType);
        if (tipo != null && (DOCUMENTOS.containsKey(tipo) || IMAGENS.contains(tipo) || AUDIO.contains(tipo))) {
            return tipo;
        }
        throw new RegraNegocioException("O ficheiro \"" + nome + "\" (" + (tipo == null ? "tipo desconhecido" : tipo)
                + ") não é aceite no chat. Tipos aceites: PDF, Word, Excel, PowerPoint, ODT/ODS, texto, CSV, imagens JPG/PNG/WebP/GIF e áudio.");
    }

    /**
     * Resposta para servir um anexo guardado. Imagens, áudio e PDF abrem no navegador; o resto
     * descarrega. Tipos fora da lista (anexos antigos) vão como binário genérico, nunca interpretados.
     */
    static ResponseEntity<byte[]> servir(ChatAnexo anexo, CacheControl cache) {
        String tipo = normalizar(anexo.getContentType());
        boolean conhecido = tipo != null && (IMAGENS.contains(tipo) || AUDIO.contains(tipo) || DOCUMENTOS.containsKey(tipo));
        boolean inline = conhecido && (IMAGENS.contains(tipo) || AUDIO.contains(tipo) || "application/pdf".equals(tipo));
        MediaType mediaType = conhecido ? MediaType.parseMediaType(tipo) : MediaType.APPLICATION_OCTET_STREAM;

        ResponseEntity.BodyBuilder r = ResponseEntity.ok().contentType(mediaType).cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff");
        if (!"application/pdf".equals(tipo)) {
            // O visualizador de PDF do Chrome não abre com sandbox; os restantes nunca precisam de scripts.
            r.header("Content-Security-Policy", "default-src 'none'; img-src 'self'; media-src 'self'; style-src 'unsafe-inline'; sandbox");
        }
        String nome = anexo.getNome() != null ? anexo.getNome() : "anexo";
        r.header(HttpHeaders.CONTENT_DISPOSITION, (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(nome, StandardCharsets.UTF_8).build().toString());
        return r.body(anexo.getDados());
    }

    static CacheControl cachePrivado(Duration duracao) {
        return CacheControl.maxAge(duracao).cachePrivate();
    }
}
