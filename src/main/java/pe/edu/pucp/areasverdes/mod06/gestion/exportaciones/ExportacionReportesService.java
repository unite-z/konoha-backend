package pe.edu.pucp.areasverdes.mod06.gestion.exportaciones;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod05.ArchivoStorage;
import pe.edu.pucp.areasverdes.mod06.comun.Periodo;
import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.IncidenciasRepository;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ServiciosTercerizadosRepository;
import pe.edu.pucp.areasverdes.mod06.gestion.exportaciones.GeneradorArchivos.Tabla;
import pe.edu.pucp.areasverdes.mod06.gestion.exportaciones.TiposExportables.TipoExportable;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ConsultaPanel;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ElementoDesglose;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.FiltrosPanel;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.MaterialVegetalService;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.Paginacion;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.PanelService;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteMaterialVegetal;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioService;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioTercerizado;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ResultadoIndicador;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.TotalMaterial;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.TotalTraslado;
import pe.edu.pucp.areasverdes.mod06.servicios.DistribucionPersonalService;

/**
 * RF-REP-009: exporta a PDF o Excel un reporte de MOD-06 y deja constancia en {@code
 * mod06_reporte_generado}. Exportar no cambia ningún dato de origen: calcula el reporte con el
 * mismo servicio que su pantalla.
 *
 * <p>Errores (contrato de {@code utils/exportaciones.ts} del frontend): 400 {@code
 * tipo-no-disponible}, {@code formato-no-permitido}, {@code parametro-invalido} y {@code
 * periodo-invalido}; 422 {@code sin-datos} y {@code volumen-excedido}.
 */
@Service
public class ExportacionReportesService {

    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** ReporteGenerado (Modelo BD 4.6.2) con los textos de interfaz. */
    public record ReporteGenerado(
            long id,
            String tipo,
            String etiquetaTipo,
            String formato,
            Map<String, Object> parametros,
            String resumenParametros,
            String usuario,
            OffsetDateTime fechaHora,
            Archivo archivo,
            int filas) {}

    public record Archivo(long id, String nombre, long tamanoBytes) {}

    /** Página del historial (Paginado de DRF). */
    public record Pagina(long count, String next, String previous, List<ReporteGenerado> results) {}

    /** El archivo de una exportación, listo para descargar. */
    public record Descarga(String nombre, String tipoMime, Resource contenido) {}

    private final ReportesGeneradosRepository repositorio;
    private final ArchivoStorage almacenamiento;
    private final PanelService panel;
    private final ReporteServicioService servicios;
    private final MaterialVegetalService material;
    private final DistribucionPersonalService distribucion;
    private final ServiciosTercerizadosRepository catalogos;
    private final IncidenciasRepository incidencias;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock reloj;

    public ExportacionReportesService(
            ReportesGeneradosRepository repositorio,
            ArchivoStorage almacenamiento,
            PanelService panel,
            ReporteServicioService servicios,
            MaterialVegetalService material,
            DistribucionPersonalService distribucion,
            ServiciosTercerizadosRepository catalogos,
            IncidenciasRepository incidencias,
            NamedParameterJdbcTemplate jdbc,
            Clock reloj) {
        this.repositorio = repositorio;
        this.almacenamiento = almacenamiento;
        this.panel = panel;
        this.servicios = servicios;
        this.material = material;
        this.distribucion = distribucion;
        this.catalogos = catalogos;
        this.incidencias = incidencias;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------ operaciones

    @Transactional
    public ReporteGenerado generar(
            String tipo, String formato, Map<String, Object> parametros, long usuarioId) {
        TipoExportable definicion =
                TiposExportables.porTipo(tipo == null ? "" : tipo)
                        .orElseThrow(
                                () ->
                                        new ErrorGestion(
                                                400,
                                                "tipo-no-disponible",
                                                "Elige un tipo de reporte exportable",
                                                Map.of(
                                                        "tipo",
                                                        List.of("No existe ese tipo de reporte"))));
        if (!definicion.disponible()) {
            throw new ErrorGestion(400, "tipo-no-disponible", definicion.motivoNoDisponible());
        }
        if (formato == null || !definicion.formatos().contains(formato)) {
            throw new ErrorGestion(
                    400,
                    "formato-no-permitido",
                    "Este reporte no se puede exportar en ese formato",
                    Map.of(
                            "formato",
                            List.of(
                                    "Usa uno de estos valores: "
                                            + String.join(", ", definicion.formatos()))));
        }
        Parametros p = Parametros.de(parametros == null ? Map.of() : parametros, definicion);
        Tabla tabla = tabla(definicion, p);
        int filas = tabla.filas().size();
        if (filas == 0) {
            throw new ErrorGestion(
                    422, "sin-datos", "No hay datos para exportar con estos filtros");
        }
        Integer limite =
                definicion.limiteFilas() == null ? null : definicion.limiteFilas().get(formato);
        if (limite != null && filas > limite) {
            throw new ErrorGestion(
                    422,
                    "volumen-excedido",
                    "El reporte tiene %s filas y el %s admite hasta %s. Acota los filtros%s."
                            .formatted(
                                    miles(filas),
                                    formato,
                                    miles(limite),
                                    definicion.formatos().size() > 1
                                            ? " o cambia de formato"
                                            : ""));
        }

        boolean pdf = formato.equals(TiposExportables.PDF);
        byte[] bytes = pdf ? GeneradorArchivos.pdf(tabla) : GeneradorArchivos.excel(tabla);
        String extension = pdf ? "pdf" : "xlsx";
        String mime = pdf ? GeneradorArchivos.MIME_PDF : GeneradorArchivos.MIME_EXCEL;
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        String nombre = "%s-%s.%s".formatted(definicion.tipo(), ahora.toLocalDate(), extension);
        String clave = "mod06/exportaciones/" + UUID.randomUUID() + "." + extension;
        almacenamiento.guardar(
                clave,
                new ArchivoStorage.Validado(
                        nombre, mime, bytes.length, sha256(bytes), extension, bytes));
        long id =
                repositorio.insertar(
                        new ReportesGeneradosRepository.Nuevo(
                                definicion.tipo(),
                                formato,
                                p.comoMapa(),
                                tabla.subtitulo(),
                                filas,
                                clave,
                                nombre,
                                mime,
                                bytes.length,
                                usuarioId,
                                ahora));
        return dto(repositorio.obtener(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public Pagina historial(String page, String tipo, Paginacion.EnlacePagina enlace) {
        long total = repositorio.contar(tipo);
        long pagina = Paginacion.resolver(page, total);
        List<ReporteGenerado> resultados =
                repositorio
                        .listar(tipo, Paginacion.TAMANO_PAGINA, Paginacion.desplazamiento(pagina))
                        .stream()
                        .map(ExportacionReportesService::dto)
                        .toList();
        long ultima = Paginacion.paginas(total);
        return new Pagina(
                total,
                pagina < ultima ? enlace.a(pagina + 1) : null,
                pagina > 1 ? enlace.a(pagina - 1) : null,
                resultados);
    }

    @Transactional(readOnly = true)
    public Descarga descargar(long id) {
        ReportesGeneradosRepository.Fila fila =
                repositorio
                        .obtener(id)
                        .orElseThrow(
                                () ->
                                        new ErrorGestion(
                                                404,
                                                "exportacion-no-encontrada",
                                                "No existe esa exportación"));
        return new Descarga(
                fila.archivoNombre(),
                fila.archivoTipoMime(),
                almacenamiento.leer(fila.archivoClave()));
    }

    // ------------------------------------------------------------------ contenido por tipo

    private Tabla tabla(TipoExportable t, Parametros p) {
        return switch (t.tipo()) {
            case "GestionOSG" -> gestion(t, p);
            case "ServicioTercerizado" -> servicio(t, p);
            case "MaterialVegetal" -> materialVegetal(t, p);
            case "DistribucionPersonal" -> distribucionPersonal(t, p);
            case "MatrizIncidencias" ->
                    new Tabla(
                            t.nombre(),
                            resumen(p),
                            MatrizIncidencias.columnas(),
                            MatrizIncidencias.filas(incidencias.conteos(p.periodo())));
            default ->
                    throw new ErrorGestion(
                            400, "tipo-no-disponible", "Elige un tipo de reporte exportable");
        };
    }

    private Tabla gestion(TipoExportable t, Parametros p) {
        RangoFechas mes = RangoFechas.delMes(p.mes());
        var resultado =
                panel.panel(
                        new ConsultaPanel(
                                mes,
                                new FiltrosPanel(
                                        mes.desde(), mes.hasta(), null, null, null, null)));
        List<List<String>> filas = new ArrayList<>();
        for (ResultadoIndicador i : resultado.indicadores()) {
            filas.add(
                    fila(
                            i.indicador().codigo(),
                            i.indicador().nombre(),
                            i.sinDatos() ? "Sin datos" : i.valor(),
                            i.indicador().unidad(),
                            i.sinDatos() ? i.mensajeSinDatos() : null));
            for (ElementoDesglose d : i.desglose()) {
                filas.add(fila(i.indicador().codigo(), "  " + d.etiqueta(), d.valor(), null, null));
            }
        }
        return new Tabla(
                t.nombre(),
                resumen(p),
                List.of("Código", "Indicador", "Valor", "Unidad", "Observación"),
                filas);
    }

    private Tabla servicio(TipoExportable t, Parametros p) {
        ReporteServicioTercerizado r =
                servicios.generar(p.periodo(), p.tipoServicioId(), p.proveedorId(), null);
        List<List<String>> filas = new ArrayList<>();
        for (ReporteServicioTercerizado.FilaAtencion a : r.atenciones()) {
            filas.add(
                    fila(
                            a.codigo(),
                            a.clase(),
                            a.estado(),
                            a.espacio(),
                            a.ejemplarCodigo(),
                            a.fechaHoraInicio() == null
                                    ? null
                                    : a.fechaHoraInicio().toLocalDate().format(DIA),
                            a.fechaHoraFin() == null
                                    ? null
                                    : a.fechaHoraFin().toLocalDate().format(DIA),
                            a.metrado(),
                            a.unidad(),
                            a.conformidad(),
                            String.valueOf(a.documentos()),
                            a.conEvidencia() ? "Sí" : "No"));
        }
        return new Tabla(
                t.nombre() + ": " + r.servicio().nombre(),
                resumen(p),
                List.of(
                        "Código",
                        "Clase",
                        "Estado",
                        "Espacio",
                        "Ejemplar",
                        "Inicio",
                        "Fin",
                        "Metrado",
                        "Unidad",
                        "Conformidad",
                        "Documentos",
                        "Evidencia"),
                filas);
    }

    private Tabla materialVegetal(TipoExportable t, Parametros p) {
        ReporteMaterialVegetal r = material.generar(p.mes(), p.areaVerdeId());
        List<List<String>> filas = new ArrayList<>();
        for (TotalMaterial m : r.consumido()) filas.add(totalMaterial("Consumo", m));
        for (TotalMaterial m : r.propagado()) filas.add(totalMaterial("Propagación", m));
        for (TotalTraslado m : r.trasladado()) {
            filas.add(
                    fila(
                            "Traslado",
                            m.origen() + " → " + m.destino(),
                            m.tipoMaterial(),
                            m.categoria(),
                            m.cantidad(),
                            m.unidad(),
                            String.valueOf(m.movimientos())));
        }
        return new Tabla(
                t.nombre(),
                resumen(p),
                List.of(
                        "Movimiento",
                        "Espacio",
                        "Material",
                        "Categoría",
                        "Cantidad",
                        "Unidad",
                        "Registros"),
                filas);
    }

    private Tabla distribucionPersonal(TipoExportable t, Parametros p) {
        Map<String, Object> r =
                distribucion.calcular(
                        new Periodo(p.periodo().desde(), p.periodo().hasta()),
                        new LinkedHashMap<>());
        List<List<String>> filas = new ArrayList<>();
        if (r.get("filas") instanceof List<?> lista) {
            for (Object o : lista) {
                if (!(o instanceof Map<?, ?> f)) continue;
                filas.add(
                        fila(
                                texto(f.get("jornada")),
                                texto(f.get("cuadrilla_nombre")),
                                texto(f.get("capataz_nombre")),
                                texto(f.get("clase_actividad_nombre")),
                                texto(f.get("tipo_actividad_nombre")),
                                texto(f.get("area_verde_nombre")),
                                texto(f.get("numero_actividades")),
                                texto(f.get("numero_operarios")),
                                texto(f.get("horas_persona"))));
            }
        }
        return new Tabla(
                t.nombre(),
                resumen(p),
                List.of(
                        "Jornada",
                        "Cuadrilla",
                        "Capataz",
                        "Clase",
                        "Actividad",
                        "Espacio",
                        "Actividades",
                        "Operarios",
                        "Horas-persona"),
                filas);
    }

    // ------------------------------------------------------------------ utilidades

    /** «Octubre de 2026 · Control fitosanitario · Jardines del Sur». */
    private String resumen(Parametros p) {
        List<String> partes = new ArrayList<>();
        if (p.mes() != null) {
            String mes = p.mes().getMonth().getDisplayName(TextStyle.FULL, ES);
            partes.add(
                    Character.toUpperCase(mes.charAt(0))
                            + mes.substring(1)
                            + " de "
                            + p.mes().getYear());
        }
        if (p.periodo() != null) {
            partes.add(p.periodo().desde().format(DIA) + " – " + p.periodo().hasta().format(DIA));
        }
        if (p.tipoServicioId() != null)
            catalogos.nombreTipoServicio(p.tipoServicioId()).ifPresent(partes::add);
        if (p.proveedorId() != null) catalogos.razonSocial(p.proveedorId()).ifPresent(partes::add);
        if (p.areaVerdeId() != null) {
            jdbc
                    .queryForList(
                            "SELECT nombre FROM mod05_areaverde WHERE id = :id",
                            Map.of("id", p.areaVerdeId()),
                            String.class)
                    .stream()
                    .findFirst()
                    .ifPresent(partes::add);
        }
        return String.join(" · ", partes);
    }

    private static List<String> totalMaterial(String movimiento, TotalMaterial m) {
        return fila(
                movimiento,
                m.espacio() == null ? "Sin espacio" : m.espacio(),
                m.tipoMaterial(),
                m.categoria(),
                m.cantidad(),
                m.unidad(),
                String.valueOf(m.movimientos()));
    }

    private static List<String> fila(String... valores) {
        return java.util.Arrays.asList(valores);
    }

    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    /** 7250 → «7 250» (separador de miles en español con espacio duro). */
    public static String miles(int n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', ' ');
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ReporteGenerado dto(ReportesGeneradosRepository.Fila f) {
        String etiqueta =
                TiposExportables.porTipo(f.tipo()).map(TipoExportable::nombre).orElse(f.tipo());
        return new ReporteGenerado(
                f.id(),
                f.tipo(),
                etiqueta,
                f.formato(),
                f.parametros(),
                f.resumenParametros(),
                f.usuario(),
                f.fechaHora(),
                new Archivo(f.id(), f.archivoNombre(), f.archivoTamanoBytes()),
                f.filas());
    }

    /** Parámetros de la exportación, ya validados para el tipo. */
    record Parametros(
            YearMonth mes,
            RangoFechas periodo,
            Long tipoServicioId,
            Long proveedorId,
            Long areaVerdeId,
            String modalidad) {

        static Parametros de(Map<String, Object> crudos, TipoExportable tipo) {
            YearMonth mes = null;
            RangoFechas periodo = null;
            if (tipo.filtros().contains("mes")) {
                Object m = crudos.get("mes");
                if (!(m instanceof String s) || !s.matches("^[0-9]{4}-(0[1-9]|1[0-2])$")) {
                    throw new ErrorGestion(
                            400,
                            "parametro-invalido",
                            "Indica el mes del reporte",
                            Map.of(
                                    "mes",
                                    List.of(
                                            "Indica el mes del reporte (mes), con el formato"
                                                    + " AAAA-MM")));
                }
                mes = YearMonth.parse(s);
            }
            if (tipo.filtros().contains("periodo")) {
                LocalDate desde = fecha(crudos.get("desde"));
                LocalDate hasta = fecha(crudos.get("hasta"));
                if (desde == null || hasta == null) {
                    throw new ErrorGestion(
                            400,
                            "periodo-invalido",
                            "Indica el periodo del reporte",
                            Map.of(
                                    desde == null ? "desde" : "hasta",
                                    List.of("Indica la fecha (AAAA-MM-DD)")));
                }
                if (hasta.isBefore(desde)) {
                    throw new ErrorGestion(
                            400,
                            "periodo-invalido",
                            Periodo.MENSAJE_ORDEN_FECHAS,
                            Map.of("hasta", List.of(Periodo.MENSAJE_ORDEN_FECHAS)));
                }
                periodo = new RangoFechas(desde, hasta);
            }
            Long tipoServicio = id(crudos, "tipo_servicio_id");
            if (tipo.tipo().equals("ServicioTercerizado") && tipoServicio == null) {
                throw new ErrorGestion(
                        400,
                        "parametro-invalido",
                        "Elige el tipo de servicio",
                        Map.of("tipo_servicio_id", List.of("Elige el tipo de servicio")));
            }
            return new Parametros(
                    mes,
                    periodo,
                    tipo.filtros().contains("tipo_servicio_id") ? tipoServicio : null,
                    tipo.filtros().contains("proveedor_id") ? id(crudos, "proveedor_id") : null,
                    tipo.filtros().contains("area_verde_id") ? id(crudos, "area_verde_id") : null,
                    tipo.filtros().contains("modalidad")
                                    && crudos.get("modalidad") instanceof String s
                            ? s
                            : null);
        }

        Map<String, Object> comoMapa() {
            Map<String, Object> r = new LinkedHashMap<>();
            if (mes != null) r.put("mes", mes.toString());
            if (periodo != null) {
                r.put("desde", periodo.desde().toString());
                r.put("hasta", periodo.hasta().toString());
            }
            if (tipoServicioId != null) r.put("tipo_servicio_id", tipoServicioId);
            if (proveedorId != null) r.put("proveedor_id", proveedorId);
            if (areaVerdeId != null) r.put("area_verde_id", areaVerdeId);
            if (modalidad != null) r.put("modalidad", modalidad);
            return r;
        }

        private static LocalDate fecha(Object valor) {
            if (!(valor instanceof String s)) return null;
            try {
                return LocalDate.parse(s);
            } catch (java.time.format.DateTimeParseException e) {
                return null;
            }
        }

        private static Long id(Map<String, Object> crudos, String campo) {
            Object valor = crudos.get(campo);
            if (valor == null) return null;
            if (valor instanceof Number n && n.longValue() > 0) return n.longValue();
            if (valor instanceof String s && s.matches("[1-9][0-9]{0,17}"))
                return Long.parseLong(s);
            throw new ErrorGestion(
                    400,
                    "parametro-invalido",
                    "Revisa los filtros del reporte",
                    Map.of(campo, List.of("Debe ser un identificador válido")));
        }
    }
}
