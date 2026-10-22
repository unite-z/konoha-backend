package pe.edu.pucp.areasverdes.mod06.api;

/**
 * Etiquetas y textos de la documentación OpenAPI ({@code @extend_schema} de {@code api/views.py}).
 *
 * <p>Trazabilidad:
 * <ul>
 *   <li>RF-REP-002 · HU-MOD06-009 · CU-MOD06-009 · CP-TR-MOD06-009</li>
 *   <li>RF-REP-004 · HU-MOD06-002 · CU-MOD06-002</li>
 *   <li>RF-REP-006 · HU-MOD06-014 · CU-MOD06-014 · CP-TR-MOD06-014</li>
 *   <li>RF-IA-001 · HU-MOD06-010 · CU-MOD06-010 · CP-TR-MOD06-010 · RN-IA-001</li>
 * </ul>
 * Las vistas de reporte son de solo lectura (GET) y nunca escriben (CU-MOD06-001: «no se modifica ningún registro
 * operativo»).
 */
public final class Documentacion {

    private Documentacion() {}

    public static final String TAG_REP_002 = "RF-REP-002 Cumplimiento del corte";
    public static final String TAG_REP_004 = "RF-REP-004 Distribución del personal";
    public static final String TAG_REP_006 = "RF-REP-006 Atenciones por espacio y sector";
    public static final String TAG_IA_001 = "RF-IA-001 Extracción de informes con IA";

    public static final String R400 = "Parámetros no válidos. Formato DRF por campo, p. ej. {\"hasta\": [\"La fecha de fin debe ser "
        + "igual o posterior a la fecha de inicio\"]}, o {codigo: \"ParametroInvalido\", detalle}.";
    public static final String R403 = "Tu rol no tiene acceso a esta función.";
    public static final String R404 = "RecursoNoEncontrado.";
    public static final String R409 = "TransicionNoPermitida: la propuesta ya fue revisada.";
    public static final String R415 = "TipoArchivoNoAdmitido: el informe no es PDF.";
    public static final String R422 = "ConfiguracionFaltante: falta el formato estándar vigente.";
    public static final String R503_DATOS = "FuenteDatosNoDisponible: no se pudieron recuperar los datos; reintenta.";
    public static final String R503_IA = "ServicioIANoDisponible o FuenteDatosNoDisponible: continúa con el registro manual o reintenta.";

    public static final String PARAM_DESDE = "Fecha de inicio del periodo (AAAA-MM-DD), incluida.";
    public static final String PARAM_HASTA = "Fecha de fin del periodo (AAAA-MM-DD), incluida.";
}
