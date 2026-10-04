package mz.netlineserv.chuangastudio.notificacao;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Formatação partilhada pelos emails e pelo PDF da fatura (pt-PT, MZN). */
public final class Formatos {

    public static final Locale PORTUGUES = Locale.forLanguageTag("pt-PT");
    public static final DateTimeFormatter DATA_EXTENSO =
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", PORTUGUES);
    public static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PORTUGUES);
    public static final DateTimeFormatter DATA_NUMERICA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private Formatos() {
    }

    public static String dinheiro(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PORTUGUES));
        return formato.format(valor) + " MZN";
    }

    public static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return "";
        }
        return nome.trim().split("\\s+")[0];
    }

    public static String codigo(UUID id) {
        return id.toString().substring(0, 8).toUpperCase();
    }

    public static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /** Só dígitos com indicativo; assume Moçambique (+258) quando o número vem sem ele, como o frontend. */
    public static String telefoneInternacional(String telefone) {
        if (telefone == null) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.startsWith("00")) {
            digitos = digitos.substring(2);
        }
        if (digitos.length() == 9) {
            digitos = "258" + digitos;
        }
        return digitos.isEmpty() ? null : digitos;
    }

    public static String whatsappUrl(String telefone) {
        String digitos = telefoneInternacional(telefone);
        return digitos == null ? null : "https://wa.me/" + digitos;
    }
}
