package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

/**
 * Representación de {@code DefinicionIndicador} (Modelo BD v1; Diagrama de Clases 4.6: {@code
 * codigo, nombre, descripcionCalculo, meta, unidad}), con la forma de {@code Indicador} en {@code
 * konoha-frontend/src/types/comun.ts} y del {@code indicador} de RF-REP-002 y RF-REP-006.
 *
 * <p>{@code meta}: texto decimal ({@code api.md}) o {@code null}; no hay metas definidas (pendiente
 * 6 de los mockups).
 */
public record Indicador(
        String codigo, String nombre, String descripcionCalculo, String meta, String unidad) {}
