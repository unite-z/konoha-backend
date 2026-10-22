package pe.edu.pucp.areasverdes.mod06.apoyo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.comun.errores.RecursoNoEncontrado;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.Entidades;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.FuenteDatos;

/**
 * Fuente de datos en memoria ({@code FuenteDatosMemoria} de {@code conftest.py}): {@code entidad → registros} y
 * {@code archivos: ruta → bytes}, con las mismas restricciones de escritura. Registra las escrituras para comprobar
 * que los reportes no escriben nada.
 */
public class FuenteDatosMemoria implements FuenteDatos {

    /** Escritura registrada: (operación, entidad, id). */
    public record Escritura(String operacion, String entidad, Object id) {}

    public final Map<String, List<Map<String, Object>>> datos = new LinkedHashMap<>();
    public final Map<String, byte[]> archivos = new LinkedHashMap<>();
    public final List<Escritura> escrituras = new ArrayList<>();

    public FuenteDatosMemoria() {
        this(Map.of(), Map.of());
    }

    public FuenteDatosMemoria(Map<String, ? extends List<? extends Map<String, ?>>> datosIniciales) {
        this(datosIniciales, Map.of());
    }

    @SuppressWarnings("unchecked")
    public FuenteDatosMemoria(Map<String, ? extends List<? extends Map<String, ?>>> datosIniciales, Map<String, byte[]> archivos) {
        for (String entidad : Entidades.CARPETA_POR_ENTIDAD.keySet()) datos.put(entidad, new ArrayList<>());
        datosIniciales.forEach((entidad, registros) -> {
            FuenteDatos.validarEntidad(entidad);
            datos.put(entidad, (List<Map<String, Object>>) Datos.normalizar(registros));
        });
        this.archivos.putAll(archivos);
    }

    @Override
    public List<Map<String, Object>> listar(String entidad, Map<String, Object> igualA) {
        FuenteDatos.validarEntidad(entidad);
        return FuenteDatos.filtrar(datos.get(entidad), igualA);
    }

    @Override
    public Map<String, Object> obtener(String entidad, Object id) {
        List<Map<String, Object>> encontrados = listar(entidad, Py.dict("id", id));
        return encontrados.isEmpty() ? null : encontrados.get(0);
    }

    @Override
    public Map<String, Object> crear(String entidad, Map<String, Object> nuevosDatos) {
        FuenteDatos.validarEscritura(entidad);
        Map<String, Object> nuevo = Py.copia(nuevosDatos);
        nuevo.put("id", FuenteDatos.siguienteId(datos.get(entidad)));
        datos.get(entidad).add(nuevo);
        escrituras.add(new Escritura("crear", entidad, nuevo.get("id")));
        return Py.copia(nuevo);
    }

    @Override
    public Map<String, Object> actualizar(String entidad, Object id, Map<String, Object> cambios) {
        FuenteDatos.validarEscritura(entidad);
        for (Map<String, Object> registro : datos.get(entidad)) {
            if (Py.eq(registro.get("id"), id)) {
                Py.copia(cambios).forEach((k, v) -> {
                    if (!k.equals("id")) registro.put(k, v);
                });
                escrituras.add(new Escritura("actualizar", entidad, id));
                return Py.copia(registro);
            }
        }
        throw new RecursoNoEncontrado("No existe el registro " + Py.str(id) + " de " + entidad);
    }

    @Override
    public byte[] leerContenidoArchivo(Map<String, Object> archivo) {
        Object ruta = archivo.get("ruta_almacenamiento");
        if (!(ruta instanceof String r) || !archivos.containsKey(r)) {
            throw new RecursoNoEncontrado("No se encontró el archivo del informe en el almacenamiento");
        }
        return archivos.get(r);
    }
}
