package pe.edu.pucp.areasverdes.comun;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Base de datos de las pruebas de integración, común a todos los módulos (AV-242).
 *
 * <p>Se configura con {@code TEST_DB_URL}, {@code TEST_DB_USER} y {@code TEST_DB_PASSWORD}; los
 * nombres anteriores, {@code MOD05_TEST_DB_*}, se siguen aceptando. Ninguna prueba lee estas
 * variables por su cuenta: si un módulo inventa las suyas, el CI no le da base ({@code
 * comprobar-higiene.sh} lo rechaza).
 */
public final class BaseDePruebas {

    public static final String URL =
            variable("URL", "jdbc:postgresql://127.0.0.1:55432/mod05_spring_test");
    public static final String USUARIO = variable("USER", System.getProperty("user.name"));
    public static final String CLAVE = variable("PASSWORD", "");

    private BaseDePruebas() {}

    /** Las suites vacían tablas: solo se admite una base cuyo nombre termina en {@code _test}. */
    public static boolean esExclusiva() {
        return URL.endsWith("_test");
    }

    /** Registra la base en el contexto de Spring; falla si no es exclusiva de pruebas. */
    public static void registrar(DynamicPropertyRegistry p) {
        if (!esExclusiva()) {
            throw new IllegalStateException(
                    "Las pruebas requieren una base exclusiva terminada en _test");
        }
        p.add("spring.datasource.url", () -> URL);
        p.add("spring.datasource.username", () -> USUARIO);
        p.add("spring.datasource.password", () -> CLAVE);
        p.add("spring.datasource.hikari.maximum-pool-size", () -> 5);
        // Spring guarda en caché más de 25 contextos y cada uno tendría su pool lleno: en CI se
        // superaba el máximo de conexiones de Postgres (100) y la suite que arrancaba entonces
        // fallaba con «too many clients». Los contextos ociosos sueltan sus conexiones.
        p.add("spring.datasource.hikari.minimum-idle", () -> 0);
        p.add("spring.datasource.hikari.idle-timeout", () -> 10_000);
    }

    private static String variable(String nombre, String porDefecto) {
        String valor = System.getenv("TEST_DB_" + nombre);
        if (valor == null) {
            valor = System.getenv("MOD05_TEST_DB_" + nombre);
        }
        return valor != null ? valor : porDefecto;
    }
}
