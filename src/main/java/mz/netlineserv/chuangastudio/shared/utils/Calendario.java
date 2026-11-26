package mz.netlineserv.chuangastudio.shared.utils;

import mz.netlineserv.chuangastudio.modules.bookings.domain.Marcacao;
import mz.netlineserv.chuangastudio.modules.bookings.domain.MarcacaoServico;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/** Evento de calendário de uma marcação: ficheiro .ics (Apple/Outlook) e link do Google Calendar. */
public final class Calendario {

    private static final ZoneId FUSO_ESTUDIO = ZoneId.of("Africa/Maputo");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private Calendario() {
    }

    public static String titulo() {
        return "Chuanga Studio";
    }

    public static String descricao(Marcacao marcacao) {
        return marcacao.getServicos().stream().map(MarcacaoServico::getNomeServico).collect(Collectors.joining(", "))
                + " · Pedido nº " + Formatos.codigo(marcacao.getId());
    }

    public static String ics(Marcacao marcacao, String localizacao, String siteUrl) {
        String inicio = utc(inicio(marcacao));
        String fim = utc(fim(marcacao));
        return String.join("\r\n",
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//Chuanga Studio//Marcacoes//PT",
                "CALSCALE:GREGORIAN",
                "METHOD:PUBLISH",
                "BEGIN:VEVENT",
                "UID:" + marcacao.getId() + "@chuangastudio",
                "DTSTAMP:" + utc(LocalDateTime.now(FUSO_ESTUDIO)),
                "DTSTART:" + inicio,
                "DTEND:" + fim,
                "SUMMARY:" + escapar(titulo()),
                "DESCRIPTION:" + escapar(descricao(marcacao) + "\n" + siteUrl + "/minha-area"),
                "LOCATION:" + escapar(localizacao),
                "BEGIN:VALARM",
                "TRIGGER:-PT2H",
                "ACTION:DISPLAY",
                "DESCRIPTION:" + escapar("Marcação no Chuanga Studio daqui a 2 horas"),
                "END:VALARM",
                "END:VEVENT",
                "END:VCALENDAR",
                "");
    }

    public static String googleUrl(Marcacao marcacao, String localizacao) {
        return "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=" + codificar(titulo())
                + "&dates=" + utc(inicio(marcacao)) + "/" + utc(fim(marcacao))
                + "&details=" + codificar(descricao(marcacao))
                + "&location=" + codificar(localizacao)
                + "&ctz=Africa/Maputo";
    }

    private static LocalDateTime inicio(Marcacao marcacao) {
        return LocalDateTime.of(marcacao.getDataMarcacao(), marcacao.getHoraMarcacao());
    }

    private static LocalDateTime fim(Marcacao marcacao) {
        int minutos = marcacao.getServicos().stream().mapToInt(i -> i.getServico().getDuracaoMinutos()).sum();
        return inicio(marcacao).plusMinutes(Math.max(minutos, 30));
    }

    private static String utc(LocalDateTime local) {
        return local.atZone(FUSO_ESTUDIO).withZoneSameInstant(ZoneOffset.UTC).format(UTC);
    }

    private static String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }
}
