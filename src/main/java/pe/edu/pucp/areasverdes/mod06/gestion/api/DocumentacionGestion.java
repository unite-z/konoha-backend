package pe.edu.pucp.areasverdes.mod06.gestion.api;

/**
 * Etiquetas y textos de la documentación OpenAPI de los reportes de gestión, con la convención de
 * {@code mod06.api.Documentacion}.
 *
 * <p>Trazabilidad:
 *
 * <ul>
 *   <li>RF-REP-001 · HU-MOD06-001 · CU-MOD06-001
 *   <li>RF-REP-005 · HU-MOD05-029 · CU-MOD05-029
 *   <li>RF-REP-007 · HU-MOD06-003 · CU-MOD06-003
 * </ul>
 *
 * Todos son de solo lectura (GET) y exigen la sesión de MOD-01 ({@code SeguridadGestion}).
 */
public final class DocumentacionGestion {

    private DocumentacionGestion() {}

    public static final String TAG_REP_001 = "RF-REP-001 Panel de indicadores";
    public static final String TAG_REP_005 = "RF-REP-005 Material vegetal";
    public static final String TAG_REP_007 = "RF-REP-007 Mapa de atenciones";

    public static final String R400 =
            "Parámetros no válidos: {codigo: validacion | periodo-invalido, detalle, errores:"
                    + " {campo: [mensajes]}}.";
    public static final String R401 =
            "Sin sesión de MOD-01 o sesión no válida: {codigo: no-autenticado, detalle}.";
    public static final String R403 =
            "Usuario sin el permiso REP.INDICADOR.CONSULTAR: {codigo: sin-permiso, detalle}.";
    public static final String R404_PAGINA =
            "Página inexistente: {codigo: pagina-inexistente, detalle}.";
    public static final String R404_INDICADOR =
            "Indicador inexistente: {codigo: indicador-no-encontrado, detalle}.";
}
