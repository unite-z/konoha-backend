package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.LongFunction;

/** Construcción del detalle paginado de un indicador, común a todas las estrategias. */
final class Detalles {

    private Detalles() {}

    /**
     * Detalle sin registros y con su motivo. La página se resuelve igual (total 0): {@code page=1}
     * vale y {@code page=2} responde 404.
     */
    static DetalleIndicador sinDatos(
            IndicadorPanel indicador,
            ConsultaPanel consulta,
            Clock reloj,
            String page,
            String motivo,
            List<String> notas) {
        Paginacion.resolver(page, 0);
        return new DetalleIndicador(
                consulta.periodo(),
                consulta.filtros(),
                OffsetDateTime.now(reloj),
                true,
                motivo,
                indicador.definicion(),
                0,
                null,
                null,
                List.of(),
                notas);
    }

    /**
     * Página de {@code total} registros; {@code registros} recibe el desplazamiento y devuelve como
     * mucho {@link Paginacion#TAMANO_PAGINA}. Sin registros, {@code mensajeSinDatos}.
     */
    static DetalleIndicador pagina(
            IndicadorPanel indicador,
            ConsultaPanel consulta,
            Clock reloj,
            long total,
            String page,
            Paginacion.EnlacePagina enlace,
            LongFunction<List<?>> registros,
            String mensajeSinDatos,
            List<String> notas) {
        long pagina = Paginacion.resolver(page, total);
        long paginas = Paginacion.paginas(total);
        List<?> resultados =
                total == 0 ? List.of() : registros.apply(Paginacion.desplazamiento(pagina));
        return new DetalleIndicador(
                consulta.periodo(),
                consulta.filtros(),
                OffsetDateTime.now(reloj),
                total == 0,
                total == 0 ? mensajeSinDatos : null,
                indicador.definicion(),
                total,
                pagina < paginas ? enlace.a(pagina + 1) : null,
                pagina > 1 ? enlace.a(pagina - 1) : null,
                List.copyOf(resultados),
                notas);
    }
}
