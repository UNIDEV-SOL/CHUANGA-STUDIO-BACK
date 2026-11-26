package mz.netlineserv.chuangastudio.shared.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean sucesso;
    /** Só nos erros: identificador estável (ex.: TOKEN_EXPIRADO) para o frontend decidir o que fazer. */
    private String codigo;
    private String mensagem;
    private T dados;
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ApiResponse<T> sucesso(T dados) {
        return new ApiResponse<>(true, null, null, dados, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> sucesso(String mensagem, T dados) {
        return new ApiResponse<>(true, null, mensagem, dados, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> erro(String mensagem) {
        return new ApiResponse<>(false, null, mensagem, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> erro(String codigo, String mensagem) {
        return new ApiResponse<>(false, codigo, mensagem, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> erro(String codigo, String mensagem, T dados) {
        return new ApiResponse<>(false, codigo, mensagem, dados, LocalDateTime.now());
    }
}
