package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod05.Atencion;
import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService;

/**
 * RF-REP-001 · HU-MOD06-001 · CU-MOD06-001: panel de indicadores de gestión del jefe de sección.
 * Reúne las cinco estrategias {@link IndicadorPanel} en el orden del CU (paso 4) y da el detalle de
 * cada una con los mismos filtros (criterio 3). Solo lectura (criterio 5).
 *
 * <p>Punto de extensión (criterio 4: «los indicadores no autorizados se ocultan o bloquean»): para
 * ocultar o bloquear indicadores por rol, {@link #panel} recibirá el {@code Usuario} y los filtrará
 * aquí. Hoy no hace falta: los cinco tienen el mismo permiso ({@code AccesoGestion}).
 */
@Service
@Transactional(readOnly = true)
public class PanelService {

    /** Orden del CU-MOD06-001, paso 4. */
    public static final List<String> ORDEN =
            List.of(
                    IndicadorActividadesPorEstado.CLAVE,
                    IndicadorDistribucionOperarios.CLAVE,
                    IndicadorAtencionesPorEspacio.CLAVE,
                    IndicadorConsumoMateriales.CLAVE,
                    IndicadorCumplimientoTercerizados.CLAVE);

    public static final List<String> MODALIDADES =
            List.of(
                    AtencionesEspacioSectorService.PERSONAL_PROPIO,
                    AtencionesEspacioSectorService.TERCERIZADO);

    /**
     * Estados válidos del filtro: el ciclo de MOD-05 implementado más los ejecutados de RF-REP-006.
     */
    public static final List<String> ESTADOS =
            List.of(
                    Atencion.REQUERIMIENTO,
                    Atencion.OC,
                    Atencion.EJECUCION,
                    Atencion.EJECUTADO,
                    Atencion.REPORTE,
                    "Observado",
                    "Conforme / cerrado");

    public static final String NOTA_FILTROS_PENDIENTES =
            "Los filtros por cuadrilla y por sector todavía no se pueden aplicar: esos datos aún no"
                    + " están disponibles en el sistema";
    public static final String MENSAJE_SIN_DATOS =
            "No hay datos para el periodo y los filtros elegidos";

    private final Map<String, IndicadorPanel> indicadores;
    private final Clock reloj;

    public PanelService(List<IndicadorPanel> indicadores, Clock reloj) {
        Map<String, IndicadorPanel> porClave = new HashMap<>();
        for (IndicadorPanel i : indicadores) {
            if (porClave.put(i.clave(), i) != null) {
                throw new IllegalStateException("Indicador del panel repetido: " + i.clave());
            }
        }
        Set<String> sobran = new HashSet<>(porClave.keySet());
        ORDEN.forEach(sobran::remove);
        if (!sobran.isEmpty()) {
            throw new IllegalStateException(
                    "Indicadores sin posición en PanelService.ORDEN: " + sobran);
        }
        Map<String, IndicadorPanel> ordenados = new LinkedHashMap<>();
        for (String clave : ORDEN) {
            IndicadorPanel i = porClave.get(clave);
            if (i == null) {
                throw new IllegalStateException("Falta el indicador del panel: " + clave);
            }
            ordenados.put(clave, i);
        }
        this.indicadores = Collections.unmodifiableMap(ordenados);
        this.reloj = reloj;
    }

    /** Los cinco indicadores del periodo con los filtros elegidos. */
    public PanelIndicadores panel(ConsultaPanel consulta) {
        List<ResultadoIndicador> resultados = new ArrayList<>();
        for (IndicadorPanel i : indicadores.values()) {
            resultados.add(i.calcular(consulta));
        }
        boolean sinDatos = resultados.stream().allMatch(ResultadoIndicador::sinDatos);
        return new PanelIndicadores(
                consulta.periodo(),
                consulta.filtros(),
                OffsetDateTime.now(reloj),
                sinDatos,
                sinDatos ? MENSAJE_SIN_DATOS : null,
                List.copyOf(resultados),
                List.of(NOTA_FILTROS_PENDIENTES));
    }

    /** Detalle paginado de un indicador; 404 {@code indicador-no-encontrado} si no existe. */
    public DetalleIndicador detalle(
            String clave, ConsultaPanel consulta, String page, Paginacion.EnlacePagina enlace) {
        IndicadorPanel i = indicadores.get(clave);
        if (i == null) {
            throw new ErrorGestion(404, "indicador-no-encontrado", "No existe ese indicador");
        }
        return i.detalle(consulta, page, enlace);
    }
}
