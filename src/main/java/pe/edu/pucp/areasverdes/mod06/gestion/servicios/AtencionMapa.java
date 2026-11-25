package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.LocalDate;

/**
 * Resumen de la atención (CU-MOD06-003, paso 6): tipo, fecha, estado, responsable (proveedor) y
 * evidencias. Siempre tiene espacio: las atenciones sin espacio no llegan al mapa (AV-408).
 *
 * <p>Una fila de personal propio es una actividad de MOD-04: {@code atencionId} es el id de la
 * actividad (puede coincidir con el de una atención tercerizada; la clave es la modalidad y el id),
 * {@code codigo} es «ACT-» y el id, {@code tipoServicio} es el tipo de actividad, {@code estado} el
 * del parte diario y {@code proveedor} la cuadrilla.
 *
 * @param proveedor el responsable: el proveedor de una atención tercerizada o la cuadrilla de una
 *     actividad (el frontend lo rotula «Responsable»)
 * @param archivosAsociados evidencias disponibles: archivos de los reportes del proveedor
 * @param tipoUsoId tipo de uso del espacio en MOD-02, para colorearlo; {@code null} si no lo tiene
 * @param areaInventarioId área de MOD-02 equivalente al espacio, para ubicarlo en el mapa del
 *     campus de MOD-02 (sus polígonos); {@code null} si no tiene equivalencia
 */
public record AtencionMapa(
        long atencionId,
        String codigo,
        String tipoAtencion,
        String tipoServicio,
        LocalDate fecha,
        String estado,
        String modalidad,
        boolean esRiesgo,
        String proveedor,
        Long areaVerdeId,
        String espacio,
        Long ejemplarId,
        String ejemplarCodigo,
        String ejemplarAlias,
        long archivosAsociados,
        Long tipoUsoId,
        Long areaInventarioId) {

    /** La fila con el tipo de uso y el área de MOD-02 de su espacio. */
    AtencionMapa conInventario(Long tipoUso, Long areaInventario) {
        return new AtencionMapa(
                atencionId,
                codigo,
                tipoAtencion,
                tipoServicio,
                fecha,
                estado,
                modalidad,
                esRiesgo,
                proveedor,
                areaVerdeId,
                espacio,
                ejemplarId,
                ejemplarCodigo,
                ejemplarAlias,
                archivosAsociados,
                tipoUso,
                areaInventario);
    }

    /**
     * Identifica la fila entre modalidades: los ids de actividades y atenciones pueden coincidir.
     */
    String clave() {
        return modalidad + ":" + atencionId;
    }
}
