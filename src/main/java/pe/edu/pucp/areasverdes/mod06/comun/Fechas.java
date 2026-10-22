package pe.edu.pucp.areasverdes.mod06.comun;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversión de fechas del JSON y fecha actual en la zona horaria del proyecto (America/Lima).
 *
 * <p>Formatos (Modelo de BD v1, tipos lógicos):
 * <ul>
 *   <li>Fecha: "AAAA-MM-DD".</li>
 *   <li>Fecha y hora: ISO 8601 con zona, por ejemplo "2026-10-20T08:30:00-05:00".</li>
 *   <li>Hora: "HH:MM".</li>
 * </ul>
 *
 * <p>Para saber si un instante pertenece a un periodo se usa su fecha <b>local</b> ({@link #fechaLocal}).
 * Las diferencias de días son días naturales [Definición acordada 20/10].
 *
 * <p>Las conversiones reproducen {@code date.fromisoformat}, {@code datetime.fromisoformat} y
 * {@code datetime.strptime} de CPython 3.14, que usaba el Django.
 */
public final class Fechas {

    private Fechas() {}

    /** Zona horaria del proyecto ({@code settings.TIME_ZONE}). Supuesto: campus en Lima. */
    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Zona horaria del proyecto. */
    public static ZoneId zonaLocal() {
        return LIMA;
    }

    /** Convierte "AAAA-MM-DD" (o un {@code LocalDate}) a fecha. Devuelve {@code null} si el valor es nulo o vacío. */
    public static LocalDate aFecha(Object valor) {
        if (valor == null || "".equals(valor)) return null;
        if (valor instanceof ZonedDateTime dt) return fechaLocal(dt);
        if (valor instanceof LocalDate d) return d;
        return fromIsoFecha(Py.str(valor));
    }

    /**
     * Convierte un texto ISO 8601 a fecha y hora <b>siempre con zona horaria</b>.
     * Si el valor no trae zona, se interpreta en la zona local del proyecto.
     */
    public static ZonedDateTime aFechaHora(Object valor) {
        if (valor == null || "".equals(valor)) return null;
        if (valor instanceof ZonedDateTime dt) return dt;
        return fromIsoFechaHora(Py.str(valor));
    }

    /** Fecha del instante {@code dt} en America/Lima. */
    public static LocalDate fechaLocal(ZonedDateTime dt) {
        return dt.withZoneSameInstant(LIMA).toLocalDate();
    }

    /** Fecha actual en la zona local del proyecto. */
    public static LocalDate hoy(Clock reloj) {
        return ZonedDateTime.now(reloj).withZoneSameInstant(LIMA).toLocalDate();
    }

    /** Instante actual con zona, expresado en la zona local del proyecto. */
    public static ZonedDateTime ahora(Clock reloj) {
        return ZonedDateTime.now(reloj).withZoneSameInstant(LIMA);
    }

    /** Instante actual en ISO 8601 con zona (America/Lima), con precisión de segundos. */
    public static String ahoraIso(Clock reloj) {
        return isoformat(ahora(reloj).truncatedTo(ChronoUnit.SECONDS));
    }

    /** {@code datetime.isoformat()} de Python: segundos siempre, microsegundos si no son cero y desfase ±HH:MM. */
    public static String isoformat(ZonedDateTime dt) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%04d-%02d-%02dT%02d:%02d:%02d", dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(),
            dt.getHour(), dt.getMinute(), dt.getSecond()));
        int micro = dt.getNano() / 1000;
        if (micro != 0) sb.append(String.format(".%06d", micro));
        int total = dt.getOffset().getTotalSeconds();
        sb.append(total < 0 ? '-' : '+');
        int abs = Math.abs(total);
        sb.append(String.format("%02d:%02d", abs / 3600, (abs % 3600) / 60));
        if (abs % 60 != 0) sb.append(String.format(":%02d", abs % 60));
        return sb.toString();
    }

    // ================================================================== date.fromisoformat (CPython)

    private static Py.ValueError invalida(String texto) {
        return new Py.ValueError("Invalid isoformat string: " + Py.repr(texto));
    }

    /** {@code date.fromisoformat(texto)} de CPython 3.14. */
    public static LocalDate fromIsoFecha(String texto) {
        byte[] b = texto.getBytes(StandardCharsets.UTF_8);
        if (b.length != 7 && b.length != 8 && b.length != 10) throw invalida(texto);
        int[] ymd = parseFecha(b, 0, b.length);
        if (ymd == null) throw invalida(texto);
        try {
            return crearFecha(ymd[0], ymd[1], ymd[2]);
        } catch (DateTimeException e) {
            throw new Py.ValueError(e.getMessage());
        }
    }

    private static LocalDate crearFecha(int y, int m, int d) {
        if (y < 1 || y > 9999) throw new DateTimeException("year " + y + " is out of range");
        return LocalDate.of(y, m, d);
    }

    private static boolean esDigito(byte[] b, int i) {
        return i < b.length && b[i] >= '0' && b[i] <= '9';
    }

    /** {@code parse_digits}: lee {@code n} dígitos ASCII o devuelve -1. */
    private static int digitos(byte[] b, int i, int n) {
        int v = 0;
        for (int k = 0; k < n; k++) {
            if (!esDigito(b, i + k)) return -1;
            v = v * 10 + (b[i + k] - '0');
        }
        return v;
    }

    private static byte en(byte[] b, int i) {
        return i < b.length ? b[i] : 0;
    }

    /** {@code parse_isoformat_date} de CPython: {año, mes, día} o {@code null}. */
    private static int[] parseFecha(byte[] b, int desde, int len) {
        int p = desde;
        int anio = digitos(b, p, 4);
        if (anio < 0) return null;
        p += 4;
        boolean separador = en(b, p) == '-';
        if (separador) p++;
        if (en(b, p) == 'W') {
            p++;
            int semana = digitos(b, p, 2);
            if (semana < 0) return null;
            p += 2;
            int dia;
            if (p - desde < len) {
                if (separador && en(b, p++) != '-') return null;
                dia = digitos(b, p, 1);
                if (dia < 0) return null;
            } else {
                dia = 1;
            }
            return isoSemana(anio, semana, dia);
        }
        int mes = digitos(b, p, 2);
        if (mes < 0) return null;
        p += 2;
        if (separador && en(b, p++) != '-') return null;
        int dia = digitos(b, p, 2);
        if (dia < 0) return null;
        return new int[] {anio, mes, dia};
    }

    private static int[] isoSemana(int anio, int semana, int dia) {
        if (semana <= 0 || semana >= 53) {
            boolean fuera = true;
            if (semana == 53) {
                LocalDate primero = LocalDate.of(Math.max(anio, 1), 1, 1);
                int diaSemana = primero.getDayOfWeek().getValue() - 1; // lunes = 0
                if (diaSemana == 3 || (diaSemana == 2 && primero.isLeapYear())) fuera = false;
            }
            if (fuera) return null;
        }
        if (dia <= 0 || dia >= 8) return null;
        LocalDate cuatroEnero = LocalDate.of(Math.max(anio, 1), 1, 4);
        LocalDate lunesSemana1 = cuatroEnero.minusDays(cuatroEnero.getDayOfWeek().getValue() - 1L);
        LocalDate r = lunesSemana1.plusWeeks(semana - 1L).plusDays(dia - 1L);
        return new int[] {r.getYear(), r.getMonthValue(), r.getDayOfMonth()};
    }

    // ================================================================== datetime.fromisoformat (CPython)

    /** {@code datetime.fromisoformat(texto)} de CPython 3.14; sin zona → America/Lima. */
    public static ZonedDateTime fromIsoFechaHora(String texto) {
        byte[] b = texto.getBytes(StandardCharsets.UTF_8);
        int len = b.length;
        if (len < 7) throw invalida(texto);
        int sep = separadorFechaHora(b, len);
        if (sep < 0) throw invalida(texto);
        int[] ymd = parseFecha(b, 0, sep);
        if (ymd == null) throw invalida(texto);
        int[] hora = {0, 0, 0, 0};
        int[] tz = null;
        if (len > sep) {
            Object[] r = parseHora(b, sep + 1, len);
            if (r == null) throw invalida(texto);
            hora = (int[]) r[0];
            tz = (int[]) r[1];
        }
        int anio = ymd[0], mes = ymd[1], dia = ymd[2];
        if (hora[0] == 24 && mes >= 1 && mes <= 12) {
            int diasMes = java.time.YearMonth.of(Math.max(anio, 1), mes).lengthOfMonth();
            if (dia <= diasMes) {
                if (hora[1] == 0 && hora[2] == 0 && hora[3] == 0) {
                    hora[0] = 0;
                    dia++;
                    if (dia > diasMes) {
                        dia = 1;
                        mes++;
                        if (mes > 12) {
                            mes = 1;
                            anio++;
                        }
                    }
                } else {
                    throw new Py.ValueError("minute, second, and microsecond must be 0 when hour is 24");
                }
            }
        }
        try {
            if (anio < 1 || anio > 9999) throw new DateTimeException("year " + anio + " is out of range");
            LocalDateTime local = LocalDateTime.of(anio, mes, dia, hora[0], hora[1], hora[2], hora[3] * 1000);
            if (tz == null) return local.atZone(LIMA);
            long desfase = (long) tz[0] * 1_000_000L + tz[1];
            if (Math.abs(desfase) >= 86_400_000_000L) {
                throw new Py.ValueError("offset must be a timedelta strictly between -timedelta(hours=24) and timedelta(hours=24).");
            }
            if (tz[1] != 0) {
                // Desfase con microsegundos: java.time solo admite segundos; se aplica al instante.
                ZonedDateTime base = local.atOffset(ZoneOffset.UTC).toZonedDateTime();
                return base.minusNanos(desfase * 1000L).withZoneSameInstant(ZoneOffset.UTC);
            }
            return local.atZone(ZoneOffset.ofTotalSeconds(tz[0]));
        } catch (DateTimeException e) {
            throw new Py.ValueError(e.getMessage());
        }
    }

    /** {@code _find_isoformat_datetime_separator} de CPython. */
    private static int separadorFechaHora(byte[] b, int len) {
        if (len == 7) return 7;
        if (en(b, 4) == '-') {
            if (en(b, 5) == 'W') {
                if (len < 8) return -1;
                if (len > 8 && en(b, 8) == '-') {
                    if (len == 9) return -1;
                    if (len > 10 && esDigito(b, 10)) return 8;
                    return 10;
                }
                return 8;
            }
            return 10;
        }
        if (en(b, 4) == 'W') {
            int idx = 7;
            for (; idx < len; idx++) if (!esDigito(b, idx)) break;
            if (idx < 9) return idx;
            return idx % 2 == 0 ? 7 : 8;
        }
        return 8;
    }

    /** {@code parse_isoformat_time}: {hora[4], tz[2] o null} o {@code null} si es inválida. */
    private static Object[] parseHora(byte[] b, int desde, int fin) {
        int tzPos = desde;
        do {
            byte c = en(b, tzPos);
            if (c == 'Z' || c == '+' || c == '-') break;
        } while (++tzPos < fin);
        int[] hora = new int[4];
        int rv = parseHhMmSsFf(b, desde, tzPos, hora);
        if (rv < 0) return null;
        if (tzPos >= fin) {
            return rv == 1 ? null : new Object[] {hora, null};
        }
        if (en(b, tzPos) == 'Z') {
            if (tzPos + 1 != fin) return null;
            return new Object[] {hora, new int[] {0, 0}};
        }
        int signo = en(b, tzPos) == '-' ? -1 : 1;
        int[] tzHora = new int[4];
        rv = parseHhMmSsFf(b, tzPos + 1, fin, tzHora);
        if (rv != 0) return null;
        int segundos = signo * (tzHora[0] * 3600 + tzHora[1] * 60 + tzHora[2]);
        return new Object[] {hora, new int[] {segundos, signo * tzHora[3]}};
    }

    /** {@code parse_hh_mm_ss_ff} de CPython: 0 si termina, 1 si queda texto, negativo si es inválida. */
    private static int parseHhMmSsFf(byte[] b, int desde, int fin, int[] valores) {
        int p = desde;
        boolean separador = true;
        for (int i = 0; i < 3; i++) {
            int v = digitos(b, p, 2);
            if (v < 0) return -3;
            valores[i] = v;
            p += 2;
            byte c = en(b, p++);
            if (i == 0) separador = c == ':';
            if (p >= fin) return c != 0 && p - 1 < b.length ? 1 : 0;
            if (separador && c == ':') {
                if (i == 2) return -4;
                continue;
            }
            if (c == '.' || c == ',') {
                if (i < 2) return -3;
                break;
            }
            if (!separador) {
                p--;
            } else {
                return -4;
            }
        }
        int resto = fin - p;
        int aLeer = Math.min(resto, 6);
        int micro = digitos(b, p, aLeer);
        if (micro < 0) return -3;
        p += aLeer;
        int[] correccion = {100000, 10000, 1000, 100, 10};
        if (aLeer > 0 && aLeer < 6) micro *= correccion[aLeer - 1];
        valores[3] = micro;
        while (esDigito(b, p)) p++;
        return p < b.length ? 1 : 0;
    }

    // ================================================================== strptime("%Y-%m-%d") (CPython)

    private static final Pattern FORMATO_AAAA_MM_DD = Pattern.compile(
        "(\\d\\d\\d\\d)-(1[0-2]|0[1-9]|[1-9])-(3[01]|[12]\\d|0[1-9]|[1-9]| [1-9])",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * {@code datetime.strptime(texto, "%Y-%m-%d").date()} de CPython: {@code null} si el texto no corresponde al
     * formato (en DRF, error «invalid»).
     */
    public static LocalDate strptimeAaaaMmDd(String texto) {
        Matcher m = FORMATO_AAAA_MM_DD.matcher(texto);
        if (!m.lookingAt() || m.end() != texto.length()) return null;
        try {
            int anio = numeroUnicode(m.group(1));
            int mes = numeroUnicode(m.group(2));
            int dia = numeroUnicode(m.group(3).strip());
            return crearFecha(anio, mes, dia);
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static int numeroUnicode(String s) {
        int v = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int d = Character.digit(cp, 10);
            if (d < 0) throw new NumberFormatException(s);
            v = v * 10 + d;
            i += Character.charCount(cp);
        }
        return v;
    }
}
