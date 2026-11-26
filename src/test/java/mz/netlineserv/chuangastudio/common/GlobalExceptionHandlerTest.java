package mz.netlineserv.chuangastudio.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import mz.netlineserv.chuangastudio.common.exception.GlobalExceptionHandler;
import mz.netlineserv.chuangastudio.common.validacao.SenhaValida;
import mz.netlineserv.chuangastudio.domain.enums.PapelUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cada tipo de erro sai com o estado HTTP certo e uma mensagem que diz exatamente o que falhou. */
class GlobalExceptionHandlerTest {

    record Pedido(@NotBlank(message = "Nome é obrigatório") String nome, PapelUsuario papel, LocalDate data,
                  @SenhaValida String senha) {
    }

    @RestController
    static class Teste {
        @GetMapping("/item/{id}")
        String item(@PathVariable UUID id) {
            return "ok";
        }

        @GetMapping("/pesquisa")
        String pesquisa(@RequestParam int pagina) {
            return "ok";
        }

        @PostMapping("/pedido")
        String pedido(@Valid @RequestBody Pedido p) {
            return "ok";
        }

        @GetMapping("/rebenta")
        String rebenta() {
            throw new IllegalStateException("estado impossível");
        }
    }

    private MockMvc mvc;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void preparar() {
        handler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(handler, "detalhados", true);
        mvc = MockMvcBuilders.standaloneSetup(new Teste()).setControllerAdvice(handler).build();
    }

    @Test
    void uuidInvalidoNoCaminho() throws Exception {
        mvc.perform(get("/item/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value(containsString("'id' tem um valor inválido ('abc')")))
                .andExpect(jsonPath("$.mensagem").value(containsString("UUID")));
    }

    @Test
    void parametroEmFalta() throws Exception {
        mvc.perform(get("/pesquisa"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_EM_FALTA"))
                .andExpect(jsonPath("$.mensagem").value(containsString("'pagina'")));
    }

    @Test
    void enumInvalidoListaOsValoresAceites() throws Exception {
        mvc.perform(post("/pedido").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"A\",\"papel\":\"CHEFE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value(containsString("'papel' tem um valor inválido ('CHEFE')")))
                .andExpect(jsonPath("$.mensagem").value(containsString("ADMIN, CLIENTE")));
    }

    @Test
    void dataInvalida() throws Exception {
        mvc.perform(post("/pedido").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"A\",\"data\":\"31/12/2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("'data'")))
                .andExpect(jsonPath("$.mensagem").value(containsString("AAAA-MM-DD")));
    }

    @Test
    void jsonMalFormadoIndicaALinha() throws Exception {
        mvc.perform(post("/pedido").contentType(MediaType.APPLICATION_JSON).content("{\"nome\": }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("JSON mal formado na linha 1")));
    }

    @Test
    void validacaoDizOCampoEOMotivo() throws Exception {
        mvc.perform(post("/pedido").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"\",\"senha\":\"abcdefgh\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.dados.nome").value("Nome é obrigatório"))
                .andExpect(jsonPath("$.dados.senha").value("A palavra-passe tem de ter pelo menos um número."));
    }

    @Test
    void metodoErradoDizOsAceites() throws Exception {
        mvc.perform(post("/item/" + UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.mensagem").value(containsString("Métodos aceites: GET")));
    }

    @Test
    void erroInesperadoTrazReferenciaEOrigem() throws Exception {
        mvc.perform(get("/rebenta"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"))
                .andExpect(jsonPath("$.dados.referencia").isNotEmpty())
                .andExpect(jsonPath("$.dados.tipo").value("IllegalStateException"))
                .andExpect(jsonPath("$.mensagem").value(containsString("estado impossível")));
    }

    @Test
    void emProducaoO500NaoMostraAMensagemInterna() throws Exception {
        ReflectionTestUtils.setField(handler, "detalhados", false);
        mvc.perform(get("/rebenta"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensagem").value(containsString("IllegalStateException")))
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.not(containsString("estado impossível"))));
    }
}
