package mz.netlineserv.chuangastudio.common.exception;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import mz.netlineserv.chuangastudio.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Traduz cada exceção numa resposta com o código HTTP certo, um {@code codigo} estável para o
 * frontend e uma mensagem que diz exatamente o que falhou. Só os erros realmente inesperados (500)
 * escondem o detalhe técnico em produção: levam uma referência que aparece no log com o stack trace.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${app.erros.detalhados:false}")
    private boolean detalhados;

    @Value("${spring.servlet.multipart.max-file-size:10MB}")
    private String maxFicheiro;

    @Value("${spring.servlet.multipart.max-request-size:20MB}")
    private String maxPedido;

    // --- Regras da aplicação ---

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ApiResponse<Object>> naoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, "RECURSO_NAO_ENCONTRADO", ex.getMessage(), null, req);
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ApiResponse<Object>> regraNegocio(RegraNegocioException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, "REGRA_NEGOCIO", ex.getMessage(), null, req);
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ApiResponse<Object>> acessoNegado(AcessoNegadoException ex, HttpServletRequest req) {
        return responder(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", ex.getMessage(), null, req);
    }

    @ExceptionHandler(LimiteExcedidoException.class)
    public ResponseEntity<ApiResponse<Object>> limiteExcedido(LimiteExcedidoException ex, HttpServletRequest req) {
        ResponseEntity<ApiResponse<Object>> r = responder(HttpStatus.TOO_MANY_REQUESTS, ex.getCodigo(), ex.getMessage(),
                Map.of("tentarDeNovoEmSegundos", ex.getSegundosParaTentarDeNovo()), req);
        return ResponseEntity.status(r.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosParaTentarDeNovo())).body(r.getBody());
    }

    // --- Autenticação e permissões ---

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> credenciaisInvalidas(BadCredentialsException ex, HttpServletRequest req) {
        String mensagem = ex.getMessage() != null && ex.getMessage().startsWith("Email") ? ex.getMessage() : "Email ou palavra-passe inválidos.";
        return responder(HttpStatus.UNAUTHORIZED, "CREDENCIAIS_INVALIDAS", mensagem, null, req);
    }

    @ExceptionHandler(ContaDesativadaException.class)
    public ResponseEntity<ApiResponse<Object>> contaDesativada(ContaDesativadaException ex, HttpServletRequest req) {
        return responder(HttpStatus.FORBIDDEN, "CONTA_DESATIVADA", ex.getMessage(), null, req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> semPermissao(AccessDeniedException ex, HttpServletRequest req) {
        return responder(HttpStatus.FORBIDDEN, "SEM_PERMISSAO",
                "A sua conta não tem permissão para " + req.getMethod() + " " + req.getServletPath() + ".", null, req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> naoAutenticado(AuthenticationException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO",
                "Falha de autenticação (" + ex.getClass().getSimpleName() + "). Entre novamente.", null, req);
    }

    // --- Dados enviados ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> validacao(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> erros = new LinkedHashMap<>();
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            erros.merge(erro.getField(), erro.getDefaultMessage(), (a, b) -> a + " " + b);
        }
        for (ObjectError erro : ex.getBindingResult().getGlobalErrors()) {
            erros.merge(erro.getObjectName(), erro.getDefaultMessage(), (a, b) -> a + " " + b);
        }
        return responder(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", resumo(erros), erros, req);
    }

    /** Restrições em @RequestParam/@PathVariable (validação de métodos do Spring 6.1). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Object>> validacaoParametros(HandlerMethodValidationException ex, HttpServletRequest req) {
        Map<String, String> erros = new LinkedHashMap<>();
        ex.getAllValidationResults().forEach(r -> {
            String nome = r.getMethodParameter().getParameterName() != null ? r.getMethodParameter().getParameterName() : "parametro" + r.getMethodParameter().getParameterIndex();
            String msgs = r.getResolvableErrors().stream().map(MessageSourceResolvable::getDefaultMessage).collect(Collectors.joining(" "));
            erros.merge(nome, msgs, (a, b) -> a + " " + b);
        });
        return responder(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", resumo(erros), erros, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> violacaoRestricao(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> erros = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String caminho = v.getPropertyPath().toString();
            String campo = caminho.contains(".") ? caminho.substring(caminho.lastIndexOf('.') + 1) : caminho;
            erros.merge(campo, v.getMessage(), (a, b) -> a + " " + b);
        }
        return responder(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", resumo(erros), erros, req);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> corpoIlegivel(org.springframework.http.converter.HttpMessageNotReadableException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "CORPO_INVALIDO", descreverCorpoInvalido(ex), null, req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> tipoParametro(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String mensagem = "O parâmetro '" + ex.getName() + "' tem um valor inválido ('" + ex.getValue() + "'). Esperado: "
                + descreverTipo(ex.getRequiredType()) + ".";
        return responder(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", mensagem, Map.of(ex.getName(), mensagem), req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> parametroEmFalta(MissingServletRequestParameterException ex, HttpServletRequest req) {
        String mensagem = "Falta o parâmetro obrigatório '" + ex.getParameterName() + "' (" + ex.getParameterType() + ") no endereço do pedido.";
        return responder(HttpStatus.BAD_REQUEST, "PARAMETRO_EM_FALTA", mensagem, Map.of(ex.getParameterName(), mensagem), req);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Object>> parteEmFalta(MissingServletRequestPartException ex, HttpServletRequest req) {
        String mensagem = "Falta o ficheiro/campo '" + ex.getRequestPartName() + "' no formulário multipart.";
        return responder(HttpStatus.BAD_REQUEST, "FICHEIRO_EM_FALTA", mensagem, Map.of(ex.getRequestPartName(), mensagem), req);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Object>> cabecalhoEmFalta(MissingRequestHeaderException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "CABECALHO_EM_FALTA", "Falta o cabeçalho obrigatório '" + ex.getHeaderName() + "'.", null, req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> uploadGrande(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return responder(HttpStatus.PAYLOAD_TOO_LARGE, "FICHEIRO_DEMASIADO_GRANDE",
                "O envio é maior do que o permitido: até " + maxFicheiro + " por ficheiro e " + maxPedido + " no total do pedido.", null, req);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Object>> multipart(MultipartException ex, HttpServletRequest req) {
        String causa = ex.getMostSpecificCause().getMessage();
        return responder(HttpStatus.BAD_REQUEST, "MULTIPART_INVALIDO",
                "Não foi possível ler o ficheiro enviado: o pedido tem de ser multipart/form-data válido"
                        + (causa != null && detalhados ? " (" + causa + ")" : "") + ".", null, req);
    }

    // --- Endereço / método / formato ---

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> endpointInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, "ENDPOINT_INEXISTENTE",
                "Não existe nenhum endpoint " + req.getMethod() + " " + req.getRequestURI() + ".", null, req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> metodo(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        String aceites = ex.getSupportedMethods() == null ? "nenhum" : String.join(", ", ex.getSupportedMethods());
        ResponseEntity<ApiResponse<Object>> r = responder(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NAO_SUPORTADO",
                "O método " + ex.getMethod() + " não é aceite em " + req.getRequestURI() + ". Métodos aceites: " + aceites + ".", null, req);
        return ResponseEntity.status(r.getStatusCode()).header(HttpHeaders.ALLOW, aceites).body(r.getBody());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> contentType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        String aceites = ex.getSupportedMediaTypes().stream().map(MediaType::toString).collect(Collectors.joining(", "));
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "CONTENT_TYPE_NAO_SUPORTADO",
                "O Content-Type '" + ex.getContentType() + "' não é aceite aqui. Use: " + aceites + ".", null, req);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Object>> accept(HttpMediaTypeNotAcceptableException ex, HttpServletRequest req) {
        String aceites = ex.getSupportedMediaTypes().stream().map(MediaType::toString).collect(Collectors.joining(", "));
        return responder(HttpStatus.NOT_ACCEPTABLE, "FORMATO_NAO_DISPONIVEL",
                "Este endpoint não devolve o formato pedido no cabeçalho Accept. Formatos disponíveis: " + aceites + ".", null, req);
    }

    // --- Base de dados ---

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> integridade(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violação de integridade em {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT, "CONFLITO_DADOS", descreverIntegridade(ex), null, req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Object>> edicaoConcorrente(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT, "CONFLITO_EDICAO",
                "Este registo foi alterado por outra pessoa enquanto o editava. Recarregue e tente de novo.", null, req);
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ApiResponse<Object>> bloqueado(CannotAcquireLockException ex, HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT, "REGISTO_OCUPADO",
                "O registo está a ser alterado noutro pedido neste momento. Tente de novo dentro de alguns segundos.", null, req);
    }

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<ApiResponse<Object>> baseDadosIndisponivel(Exception ex, HttpServletRequest req) {
        String ref = referencia();
        log.error("[{}] Base de dados indisponível em {} {}", ref, req.getMethod(), req.getRequestURI(), ex);
        return responder(HttpStatus.SERVICE_UNAVAILABLE, "BASE_DADOS_INDISPONIVEL",
                "Não foi possível ligar à base de dados (ref. " + ref + ")"
                        + (detalhados ? ": " + NestedExceptionUtils.getMostSpecificCause(ex).getMessage() : "") + ". Tente de novo dentro de instantes.",
                Map.of("referencia", ref), req);
    }

    // --- Outros ---

    /** ResponseStatusException e afins: já trazem o estado e o motivo. */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiResponse<Object>> comEstado(ErrorResponseException ex, HttpServletRequest req) {
        HttpStatusCode estado = ex.getStatusCode();
        String motivo = ex.getBody().getDetail() != null ? ex.getBody().getDetail() : ex.getMessage();
        return responder(HttpStatus.valueOf(estado.value()), "ERRO_" + estado.value(), motivo, null, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> inesperado(Exception ex, HttpServletRequest req) {
        String ref = referencia();
        log.error("[{}] Erro inesperado em {} {}", ref, req.getMethod(), req.getRequestURI(), ex);

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("referencia", ref);
        dados.put("tipo", ex.getClass().getSimpleName());
        String mensagem;
        if (detalhados) {
            String local = localNaAplicacao(ex);
            dados.put("detalhe", ex.getMessage());
            if (local != null) dados.put("local", local);
            mensagem = "Erro inesperado (ref. " + ref + "): " + ex.getClass().getSimpleName()
                    + (ex.getMessage() != null ? " — " + ex.getMessage() : "") + (local != null ? " [em " + local + "]" : "");
        } else {
            mensagem = "Erro inesperado no servidor (ref. " + ref + ", " + ex.getClass().getSimpleName()
                    + "). O detalhe completo ficou registado no log com esta referência.";
        }
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", mensagem, dados, req);
    }

    // --- Auxiliares ---

    private ResponseEntity<ApiResponse<Object>> responder(HttpStatus status, String codigo, String mensagem, Object dados,
                                                          HttpServletRequest req) {
        if (status.is4xxClientError()) {
            log.info("{} {} → {} {}: {}", req.getMethod(), req.getRequestURI(), status.value(), codigo, mensagem);
        }
        return ResponseEntity.status(status).body(ApiResponse.erro(codigo, mensagem, dados));
    }

    private static String resumo(Map<String, String> erros) {
        if (erros.isEmpty()) return "Dados inválidos.";
        if (erros.size() == 1) {
            Map.Entry<String, String> e = erros.entrySet().iterator().next();
            return "Dados inválidos em '" + e.getKey() + "': " + e.getValue();
        }
        return "Dados inválidos em " + erros.size() + " campos: " + erros.entrySet().stream()
                .map(e -> e.getKey() + " — " + semPontoFinal(e.getValue())).collect(Collectors.joining("; ")) + ".";
    }

    private static String semPontoFinal(String texto) {
        return texto != null && texto.endsWith(".") ? texto.substring(0, texto.length() - 1) : texto;
    }

    private String descreverCorpoInvalido(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        Throwable causa = ex.getCause();
        if (causa == null) {
            return "O corpo do pedido (JSON) está em falta ou vazio.";
        }
        if (causa instanceof InvalidFormatException f) {
            return "O campo '" + caminho(f) + "' tem um valor inválido (" + valor(f.getValue()) + "). Esperado: "
                    + descreverTipo(f.getTargetType()) + ".";
        }
        if (causa instanceof UnrecognizedPropertyException u) {
            return "O campo '" + u.getPropertyName() + "' não existe neste pedido.";
        }
        if (causa instanceof MismatchedInputException m) {
            String campo = caminho(m);
            return campo.isEmpty()
                    ? "O corpo do pedido não tem o formato esperado (" + descreverTipo(m.getTargetType()) + ")."
                    : "O campo '" + campo + "' tem o tipo errado. Esperado: " + descreverTipo(m.getTargetType()) + ".";
        }
        if (causa instanceof JsonParseException p) {
            return "JSON mal formado na linha " + p.getLocation().getLineNr() + ", coluna " + p.getLocation().getColumnNr()
                    + ": " + p.getOriginalMessage() + ".";
        }
        if (causa instanceof JsonMappingException m) {
            String campo = caminho(m);
            Throwable raiz = m.getCause() != null ? m.getCause() : m;
            String motivo = raiz instanceof JsonMappingException jm ? jm.getOriginalMessage() : raiz.getMessage();
            return (campo.isEmpty() ? "O corpo do pedido é inválido" : "O campo '" + campo + "' é inválido") + ": " + motivo + ".";
        }
        return "Não foi possível ler o corpo do pedido: " + causa.getMessage();
    }

    private static String caminho(JsonMappingException ex) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) sb.append('.');
                sb.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    private static String valor(Object v) {
        if (v == null) return "vazio";
        String s = String.valueOf(v);
        return "'" + (s.length() > 60 ? s.substring(0, 60) + "…" : s) + "'";
    }

    private static String descreverTipo(Class<?> tipo) {
        if (tipo == null) return "outro formato";
        if (tipo.isEnum()) {
            return "um destes valores: " + Arrays.stream(tipo.getEnumConstants()).map(String::valueOf).collect(Collectors.joining(", "));
        }
        if (tipo == UUID.class) return "um identificador UUID (ex.: 3fa85f64-5717-4562-b3fc-2c963f66afa6)";
        if (tipo == LocalDate.class) return "uma data no formato AAAA-MM-DD";
        if (tipo == LocalTime.class) return "uma hora no formato HH:MM";
        if (tipo == LocalDateTime.class) return "data e hora no formato AAAA-MM-DDTHH:MM:SS";
        if (tipo == Integer.class || tipo == int.class || tipo == Long.class || tipo == long.class
                || tipo == Short.class || tipo == short.class) return "um número inteiro";
        if (tipo == BigDecimal.class || tipo == Double.class || tipo == double.class) return "um número (use ponto nas décimas)";
        if (tipo == Boolean.class || tipo == boolean.class) return "true ou false";
        if (tipo == String.class) return "texto";
        if (java.util.Collection.class.isAssignableFrom(tipo) || tipo.isArray()) return "uma lista [...]";
        return "um objeto " + tipo.getSimpleName();
    }

    private static final Pattern DUPLICADO = Pattern.compile("Key \\((.+?)\\)=\\((.*?)\\) already exists");
    private static final Pattern FK_INEXISTENTE = Pattern.compile("Key \\((.+?)\\)=\\((.*?)\\) is not present in table \"(.+?)\"");
    private static final Pattern FK_EM_USO = Pattern.compile("is still referenced from table \"(.+?)\"");
    private static final Pattern NULO = Pattern.compile("null value in column \"(.+?)\"");
    private static final Pattern LONGO = Pattern.compile("value too long for type character varying\\((\\d+)\\)");

    private static String descreverIntegridade(DataIntegrityViolationException ex) {
        SQLException sql = null;
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException s) sql = s;
        }
        String estado = sql != null ? sql.getSQLState() : null;
        String texto = sql != null && sql.getMessage() != null ? sql.getMessage() : String.valueOf(ex.getMostSpecificCause().getMessage());
        Matcher m;
        if ("23505".equals(estado) && (m = DUPLICADO.matcher(texto)).find()) {
            return "Já existe um registo com " + m.group(1) + " = '" + m.group(2) + "'. Esse valor tem de ser único.";
        }
        if ("23503".equals(estado) && (m = FK_INEXISTENTE.matcher(texto)).find()) {
            return "O registo indicado em " + m.group(1) + " ('" + m.group(2) + "') não existe (tabela " + m.group(3) + ").";
        }
        if ("23503".equals(estado) && (m = FK_EM_USO.matcher(texto)).find()) {
            return "Não é possível apagar ou alterar: este registo ainda é usado em '" + m.group(1) + "'.";
        }
        if ("23502".equals(estado) && (m = NULO.matcher(texto)).find()) {
            return "O campo '" + m.group(1) + "' é obrigatório e veio vazio.";
        }
        if ("22001".equals(estado) && (m = LONGO.matcher(texto)).find()) {
            return "Um dos textos enviados excede o tamanho máximo de " + m.group(1) + " caracteres.";
        }
        if ("23514".equals(estado)) {
            return "Um dos valores enviados não cumpre as regras da base de dados (" + primeiraLinha(texto) + ").";
        }
        return "Os dados entram em conflito com registos existentes: " + primeiraLinha(texto);
    }

    private static String primeiraLinha(String texto) {
        int nl = texto.indexOf('\n');
        return (nl >= 0 ? texto.substring(0, nl) : texto).replace("ERROR: ", "").trim();
    }

    /** Primeira linha do stack trace dentro do código da aplicação (onde o erro nasceu). */
    private static String localNaAplicacao(Throwable ex) {
        for (StackTraceElement e : ex.getStackTrace()) {
            if (e.getClassName().startsWith("mz.netlineserv.")) {
                return e.getFileName() + ":" + e.getLineNumber() + " (" + e.getMethodName() + ")";
            }
        }
        return null;
    }

    private static String referencia() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
