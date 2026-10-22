package pe.edu.pucp.areasverdes.mod05;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Arrays;

/** Estado del agregado utilizado para validar transiciones bajo bloqueo de fila. */
public record Atencion(long id, String estado, String origen, boolean esRiesgo,
                       String ordenCompra, OffsetDateTime inicio, OffsetDateTime fin) {
    public static final String REQUERIMIENTO = "Requerimiento registrado", OC = "OC referenciada",
        EJECUCION = "En ejecución", EJECUTADO = "Ejecutado pendiente de reporte", REPORTE = "Reporte adjunto";
    public static Atencion from(Map<String,Object> row) {
        return new Atencion(((Number)row.get("id")).longValue(), (String)row.get("estado"), (String)row.get("origen"),
            (boolean)row.get("es_riesgo"), (String)row.get("numero_orden_compra"),
            (OffsetDateTime)row.get("fecha_hora_inicio"), (OffsetDateTime)row.get("fecha_hora_fin"));
    }
    public void exigir(String... permitidos) {
        if (!Arrays.asList(permitidos).contains(estado)) throw ApiError.conflict("Operación no permitida desde " + estado);
    }
}
