package pe.edu.pucp.areasverdes.mod06.gestion.datos;

/**
 * Enumerado TipoMovimiento del Modelo BD v1 (4.4.12): consumo, propagación o traslado.
 *
 * <p>Interno: no se serializa. Los literales documentados (Modelo BD v1, enumerado TipoMovimiento =
 * Consumo; Propagacion; Traslado) son los que traducirá el adaptador de MOD-04 (como {@code
 * CATEGORIA_MOD05} en {@code FuenteReportesProveedorMod05}).
 */
public enum TipoMovimiento {
    CONSUMO,
    PROPAGACION,
    TRASLADO
}
