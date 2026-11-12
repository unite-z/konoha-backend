package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ActividadesMapaRepository;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ActividadesMapaRepository.ActividadMapa;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.AtencionRealizada;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.AtencionesTercerizadasRepository;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.Disponibilidad;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FiltroAtenciones;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteOperacionCampo;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteTiposUso;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteUbicaciones;
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService;

/**
 * RF-REP-007 · HU-MOD06-003 · CU-MOD06-003: mapa de las atenciones de mantenimiento, poda y control
 * fitosanitario de un periodo. Devuelve el GeoJSON y, siempre, la lista tabular (A1 y E1). Solo
 * lectura.
 *
 * <ul>
 *   <li>Atenciones tercerizadas (MOD-05), por su tipo de servicio, y actividades del personal
 *       propio (MOD-04), por su clase: mantenimiento de jardines, poda y manejo fitosanitario.
 *   <li>Los filtros de tipo de servicio, estado y riesgo son de MOD-05: con alguno, solo cuentan
 *       las atenciones tercerizadas (una actividad no tiene riesgo, así que «sin riesgo» sí las
 *       deja).
 *   <li>Sin geometrías (MOD-02) las atenciones van a {@code sinCoordenadas}; no es «Sin datos»: las
 *       atenciones existen y se listan.
 *   <li>Cada fila lleva el tipo de uso de su espacio (MOD-02) para colorearlo en el mapa.
 *   <li>El «historial del espacio» (paso 7) es la misma consulta con {@code area_verde_id}.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class MapaAtencionesService {

    /**
     * Estados que filtra el mapa (solo atenciones realizadas), en un orden fijo para el mensaje.
     */
    public static final List<String> ESTADOS_REALIZADOS =
            List.of(
                    "Ejecutado pendiente de reporte",
                    "Reporte adjunto",
                    "Observado",
                    "Conforme / cerrado");

    public static final String MENSAJE_SIN_DATOS =
            "No hay atenciones para el periodo y los filtros elegidos";
    public static final String NOTA_SIN_CLASIFICAR =
            "Atenciones de otros servicios (no son mantenimiento, poda ni control fitosanitario)"
                    + " que no se muestran: %d";
    public static final String NOTA_ACTIVIDADES_SIN_CLASIFICAR =
            "Actividades del personal propio de otras clases (no son mantenimiento, poda ni manejo"
                    + " fitosanitario) que no se muestran: %d";
    // CONFIRMADO (decisión del responsable, 28/10/2026): ver TiposAtencion.
    public static final String NOTA_CORTE = "El corte de césped se muestra como mantenimiento";

    /** Prefijo del código de una actividad del personal propio en el mapa. */
    public static final String PREFIJO_ACTIVIDAD = "ACT-";

    private final AtencionesTercerizadasRepository atenciones;
    private final ActividadesMapaRepository actividades;
    private final FuenteUbicaciones ubicaciones;
    private final FuenteTiposUso tiposUso;
    private final FuenteOperacionCampo operacion;
    private final Clock reloj;

    public MapaAtencionesService(
            AtencionesTercerizadasRepository atenciones,
            ActividadesMapaRepository actividades,
            FuenteUbicaciones ubicaciones,
            FuenteTiposUso tiposUso,
            FuenteOperacionCampo operacion,
            Clock reloj) {
        this.atenciones = atenciones;
        this.actividades = actividades;
        this.ubicaciones = ubicaciones;
        this.tiposUso = tiposUso;
        this.operacion = operacion;
        this.reloj = reloj;
    }

    public MapaAtenciones mapa(RangoFechas periodo, FiltrosMapa filtros) {
        OffsetDateTime generadoEn = OffsetDateTime.now(reloj);
        boolean conTercerizadas =
                !AtencionesEspacioSectorService.PERSONAL_PROPIO.equals(filtros.modalidad());
        boolean conPropias =
                !AtencionesEspacioSectorService.TERCERIZADO.equals(filtros.modalidad())
                        && filtros.tipoServicioId() == null
                        && filtros.estado() == null
                        && !Boolean.TRUE.equals(filtros.esRiesgo());

        // 1. Personal propio pedido sin MOD-04 en el sistema: no se inventan ceros.
        Disponibilidad campo = operacion.disponibilidad();
        if (!conTercerizadas && !campo.disponible()) {
            return vacio(periodo, filtros, generadoEn, campo.motivo());
        }
        // 2. Filtro de tipo de uso sin tipo de uso en el sistema.
        Disponibilidad usos = tiposUso.disponibilidad();
        if (filtros.tipoUsoId() != null && !usos.disponible()) {
            return vacio(periodo, filtros, generadoEn, usos.motivo());
        }

        // 3 y 4. Consulta y clasificación por tipo de atención.
        List<String> notas = new ArrayList<>();
        List<AtencionMapa> clasificadas = new ArrayList<>();
        if (conTercerizadas) {
            Set<Long> sinClasificar = new LinkedHashSet<>();
            for (AtencionRealizada f :
                    atenciones.atencionesRealizadas(
                            new FiltroAtenciones(
                                    periodo,
                                    filtros.areaVerdeId(),
                                    filtros.tipoServicioId(),
                                    filtros.estado()),
                            filtros.esRiesgo())) {
                var tipo = TiposAtencion.deTipoServicio(f.tipoServicio());
                if (tipo.isEmpty()) sinClasificar.add(f.atencionId());
                else if (pedido(filtros, tipo.get())) clasificadas.add(comoAtencion(f, tipo.get()));
            }
            if (!sinClasificar.isEmpty()) {
                notas.add(NOTA_SIN_CLASIFICAR.formatted(sinClasificar.size()));
            }
        }
        if (conPropias && campo.disponible()) {
            int sinClasificar = 0;
            for (ActividadMapa a : actividades.actividades(periodo, filtros.areaVerdeId())) {
                var tipo = TiposAtencion.deClaseActividad(a.clase());
                if (tipo.isEmpty()) sinClasificar++;
                else if (pedido(filtros, tipo.get()))
                    clasificadas.add(comoActividad(a, tipo.get()));
            }
            if (sinClasificar > 0) {
                notas.add(NOTA_ACTIVIDADES_SIN_CLASIFICAR.formatted(sinClasificar));
            }
        }
        // De la más reciente a la más antigua; con la misma fecha, en el orden de lectura.
        clasificadas.sort(Comparator.comparing(AtencionMapa::fecha).reversed());

        // 5. Tipo de uso de cada espacio (para colorearlo) y, si se filtra, solo los de ese tipo.
        if (usos.disponible()) {
            Map<Long, Long> usoPorEspacio = tiposUso.tipoUsoDeEspacios(ids(clasificadas, true));
            clasificadas =
                    clasificadas.stream()
                            .map(
                                    a ->
                                            a.conTipoUso(
                                                    a.areaVerdeId() == null
                                                            ? null
                                                            : usoPorEspacio.get(a.areaVerdeId())))
                            .filter(
                                    a ->
                                            filtros.tipoUsoId() == null
                                                    || filtros.tipoUsoId().equals(a.tipoUsoId()))
                            .collect(Collectors.toCollection(ArrayList::new));
        }

        // 6. Geometrías: la del ejemplar si la fila es un ejemplar; si no, la del espacio.
        List<ElementoGeoJson> features = new ArrayList<>();
        List<AtencionMapa> sinCoordenadas = new ArrayList<>();
        Disponibilidad geo = ubicaciones.disponibilidad();
        if (geo.disponible()) {
            Map<Long, JsonNode> deEspacios =
                    ubicaciones.geometriasDeEspacios(ids(clasificadas, true));
            Map<Long, JsonNode> deEjemplares =
                    ubicaciones.geometriasDeEjemplares(ids(clasificadas, false));
            for (AtencionMapa a : clasificadas) {
                JsonNode geometria =
                        a.ejemplarId() != null
                                ? deEjemplares.get(a.ejemplarId())
                                : a.areaVerdeId() == null ? null : deEspacios.get(a.areaVerdeId());
                if (geometria == null) {
                    sinCoordenadas.add(a);
                } else {
                    features.add(ElementoGeoJson.de(geometria, a));
                }
            }
        } else {
            sinCoordenadas.addAll(clasificadas);
            notas.add(geo.motivo());
        }

        // 7. Totales por tipo (atenciones distintas): conteos reales, el 0 se permite.
        List<TotalTipoAtencion> totales = new ArrayList<>();
        for (String tipo : TiposAtencion.TODOS) {
            long total =
                    clasificadas.stream()
                            .filter(a -> a.tipoAtencion().equals(tipo))
                            .map(AtencionMapa::clave)
                            .distinct()
                            .count();
            totales.add(new TotalTipoAtencion(tipo, total));
        }

        // 9. Notas.
        if (!AtencionesEspacioSectorService.TERCERIZADO.equals(filtros.modalidad())
                && !campo.disponible()) {
            notas.add(IndicadorAtencionesPorEspacio.NOTA_SOLO_TERCERIZADAS);
        }
        notas.add(NOTA_CORTE);

        // 8 y 10. Sin filas: el mapa sigue visible, con features vacío.
        boolean sinDatos = clasificadas.isEmpty();
        return new MapaAtenciones(
                periodo,
                filtros,
                generadoEn,
                sinDatos,
                sinDatos ? MENSAJE_SIN_DATOS : null,
                DefinicionesIndicador.MAPA_ATENCIONES,
                List.copyOf(totales),
                ColeccionGeoJson.de(features),
                List.copyOf(sinCoordenadas),
                List.copyOf(notas));
    }

    /** «Sin datos» sin consultar: listas vacías, sin totales y el mapa visible y vacío. */
    private MapaAtenciones vacio(
            RangoFechas periodo, FiltrosMapa filtros, OffsetDateTime generadoEn, String motivo) {
        return new MapaAtenciones(
                periodo,
                filtros,
                generadoEn,
                true,
                motivo,
                DefinicionesIndicador.MAPA_ATENCIONES,
                List.of(),
                ColeccionGeoJson.de(List.of()),
                List.of(),
                List.of());
    }

    // CONFIRMADO: Modelo BD v1 4.5.4, AtencionTercerizada no tiene responsable propio (la ejecuta
    // el proveedor del contrato) y su dato de riesgo es es_riesgo (RN-009). Por eso el
    // «responsable» del CU (paso 6) es el proveedor, y el «nivel de riesgo» (criterio 1), es el
    // campo es_riesgo.
    private static AtencionMapa comoAtencion(AtencionRealizada f, String tipoAtencion) {
        return new AtencionMapa(
                f.atencionId(),
                f.codigo(),
                tipoAtencion,
                f.tipoServicio(),
                f.fecha(),
                f.estado(),
                AtencionesEspacioSectorService.TERCERIZADO,
                f.esRiesgo(),
                f.proveedor(),
                f.areaVerdeId(),
                f.areaVerdeId() == null ? AgrupadorMaterial.SIN_ESPACIO : f.espacio(),
                f.ejemplarId(),
                f.ejemplarCodigo(),
                f.archivosAsociados(),
                null);
    }

    /** Una actividad del personal propio como fila del mapa (ver {@link AtencionMapa}). */
    private static AtencionMapa comoActividad(ActividadMapa a, String tipoAtencion) {
        return new AtencionMapa(
                a.id(),
                PREFIJO_ACTIVIDAD + a.id(),
                tipoAtencion,
                a.tipoActividad(),
                a.fecha(),
                a.estadoParte(),
                AtencionesEspacioSectorService.PERSONAL_PROPIO,
                false,
                a.cuadrilla(),
                a.areaVerdeId(),
                a.areaVerdeId() == null ? AgrupadorMaterial.SIN_ESPACIO : a.espacio(),
                a.ejemplarId(),
                a.ejemplarCodigo(),
                0,
                null);
    }

    /** Si el tipo de atención entra con el filtro de tipo de atención. */
    private static boolean pedido(FiltrosMapa filtros, String tipo) {
        return filtros.tipoAtencion() == null || filtros.tipoAtencion().equals(tipo);
    }

    /** Ids distintos de espacios ({@code espacios}) o de ejemplares de las atenciones. */
    private static Set<Long> ids(List<AtencionMapa> atenciones, boolean espacios) {
        return atenciones.stream()
                .map(a -> espacios ? a.areaVerdeId() : a.ejemplarId())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
