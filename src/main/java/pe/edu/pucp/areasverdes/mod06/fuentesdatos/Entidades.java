package pe.edu.pucp.areasverdes.mod06.fuentesdatos;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registro de entidades: carpeta del módulo propietario y entidades que el MOD-06 puede escribir.
 *
 * <p>Fuente: Modelo de BD v1, tabla 2 (módulo propietario de cada entidad) y sección 2.1
 * («el módulo propietario mantiene la entidad y los demás solo la consultan»).
 */
public final class Entidades {

    private Entidades() {}

    private static final Map<String, List<String>> ENTIDADES_POR_CARPETA = new LinkedHashMap<>();

    static {
        ENTIDADES_POR_CARPETA.put("mod01", List.of("Usuario", "Rol"));
        ENTIDADES_POR_CARPETA.put("mod02", List.of("Lugar", "SectorMantenimiento", "AsignacionSector", "Cuartel",
            "AreaVerde", "RutaCorte", "Ejemplar", "IntervencionArbolado"));
        ENTIDADES_POR_CARPETA.put("mod03", List.of("TipoServicio", "FrecuenciaServicio", "DuracionServicio",
            "ProgramacionServicio", "CicloCorte"));
        ENTIDADES_POR_CARPETA.put("mod04", List.of("Personal", "Cuadrilla", "AsignacionCuadrilla", "ClaseActividad",
            "TipoActividad", "UnidadMedida", "ParteDiario", "Actividad", "ParticipacionActividad"));
        ENTIDADES_POR_CARPETA.put("mod05", List.of("Proveedor", "Contrato", "AtencionTercerizada", "AlcanceAtencion",
            "ReporteProveedor", "FormatoReporteServicio", "CampoFormatoReporte", "Plaga", "ProductoAgronomico",
            "AplicacionFitosanitaria", "AplicacionObjetivo"));
        ENTIDADES_POR_CARPETA.put("mod06", List.of("DefinicionIndicador", "ReporteGenerado", "SugerenciaIA"));
        ENTIDADES_POR_CARPETA.put("transversal", List.of("Archivo", "ArchivoAsociado"));
    }

    /** Entidad → carpeta del módulo propietario dentro de {@code mock-data/}. */
    public static final Map<String, String> CARPETA_POR_ENTIDAD;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        ENTIDADES_POR_CARPETA.forEach((carpeta, entidades) -> entidades.forEach(e -> m.put(e, carpeta)));
        CARPETA_POR_ENTIDAD = Collections.unmodifiableMap(m);
    }

    /** Entidades propias del MOD-06: las únicas en las que este módulo puede escribir. */
    public static final Set<String> ENTIDADES_ESCRIBIBLES = Set.of("SugerenciaIA", "ReporteGenerado");

    /** Asociaciones con clave compuesta (sin atributo {@code id}). */
    public static final Set<String> ENTIDADES_SIN_ID = Set.of("ParticipacionActividad");
}
