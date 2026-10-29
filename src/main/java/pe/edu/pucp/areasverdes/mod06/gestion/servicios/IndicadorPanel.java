package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

/**
 * Estrategia de cálculo de un indicador del panel (patrón Estrategia, Diagrama de Clases v1,
 * sección 7; DefinicionIndicador.calcular(periodo), 4.6). Cada indicador tiene su propia fuente y
 * fórmula. Para añadir uno se crea otra implementación @Component y se añade su clave a
 * PanelService.ORDEN, sin tocar el resto.
 */
public interface IndicadorPanel {

    /**
     * Clave estable en kebab-case, usada en la ruta del detalle: /api/v1/reportes/panel/{clave}/.
     */
    String clave();

    Indicador definicion();

    ResultadoIndicador calcular(ConsultaPanel consulta);

    /**
     * Detalle de los registros con los mismos filtros (HU-MOD06-001, criterio 3), paginado de 25.
     */
    DetalleIndicador detalle(ConsultaPanel consulta, String page, Paginacion.EnlacePagina enlace);
}
