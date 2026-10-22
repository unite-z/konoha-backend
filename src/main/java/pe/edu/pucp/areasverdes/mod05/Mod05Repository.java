package pe.edu.pucp.areasverdes.mod05;

import java.util.*;
import java.sql.*;
import java.time.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class Mod05Repository {
    final NamedParameterJdbcTemplate jdbc;
    final ObjectMapper json;
    public Mod05Repository(NamedParameterJdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public NamedParameterJdbcTemplate jdbc() { return jdbc; }
    static final RowMapper<Map<String,Object>> ROW = (rs, i) -> {
        Map<String,Object> row = new LinkedHashMap<>();
        var meta = rs.getMetaData();
        for (int c=1;c<=meta.getColumnCount();c++) {
            Object value = rs.getObject(c);
            if (value instanceof Timestamp timestamp) value = timestamp.toInstant().atZone(Mod05Application.LIMA).toOffsetDateTime();
            else if (value instanceof java.sql.Date date) value = date.toLocalDate();
            row.put(meta.getColumnLabel(c), value);
        }
        return row;
    };
    public List<Map<String,Object>> rows(String sql, Map<String,?> params) { return jdbc.query(sql, params, ROW); }
    public Map<String,Object> one(String sql, Map<String,?> params) {
        var rows = rows(sql, params);
        if (rows.isEmpty()) throw ApiError.missing("No existe el registro solicitado");
        return rows.getFirst();
    }
    public Map<String,Object> get(String table, long id) { return one("SELECT * FROM mod05_"+table+" WHERE id=:id", Map.of("id",id)); }
    public Map<String,Object> lock(String table, long id) { return one("SELECT * FROM mod05_"+table+" WHERE id=:id FOR UPDATE", Map.of("id",id)); }
    // Los nombres de tablas/columnas provienen exclusivamente del código, nunca de la petición.
    public long insert(String table, Map<String,Object> values) {
        String columns=String.join(",",values.keySet());
        String params=String.join(",",values.keySet().stream().map(k -> ":"+k).toList());
        return jdbc.queryForObject("INSERT INTO mod05_"+table+" ("+columns+") VALUES ("+params+") RETURNING id", values, Long.class);
    }
    public void update(String table, long id, Map<String,Object> values) {
        var params = new HashMap<String,Object>(values); params.put("id",id);
        String sets = String.join(",",values.keySet().stream().map(k -> k+"=:"+k).toList());
        jdbc.update("UPDATE mod05_"+table+" SET "+sets+" WHERE id=:id",params);
    }
    public static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    public Optional<Usuario> usuario(String username) {
        var users=rows("SELECT * FROM mod05_usuario WHERE usuario=:u",Map.of("u",username));
        if (users.isEmpty()) return Optional.empty();
        var u=users.getFirst(); long id=number(u,"id");
        List<String> roles=jdbc.queryForList("SELECT rol FROM mod05_usuario_rol WHERE usuario_id=:id",Map.of("id",id),String.class);
        return Optional.of(new Usuario(id,(String)u.get("usuario"),(String)u.get("nombre"),(String)u.get("password_hash"),(boolean)u.get("activo"),roles));
    }
    public void audit(String entity,long id,String action,Usuario user,Map<String,Object> before,Map<String,Object> after,String motivo) {
        try {
            jdbc.update("""
                INSERT INTO mod05_registroauditoria(entidad,registro_id,accion,usuario_id,fecha_hora,valores_anteriores,valores_nuevos,motivo)
                VALUES (:entidad,:id,:accion,:usuario,now(),CAST(:antes AS jsonb),CAST(:despues AS jsonb),:motivo)
                """,Map.of("entidad",entity,"id",id,"accion",action,"usuario",user.id(),"antes",json.writeValueAsString(before),
                    "despues",json.writeValueAsString(after),"motivo",motivo));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("No se pudo serializar la auditoría",e); }
    }
    public void history(long id,String before,String after,Usuario user) {
        if (!before.equals(after)) jdbc.update("""
            INSERT INTO mod05_historialestado(atencion_id,estado_anterior,estado_nuevo,usuario_id,fecha_hora)
            VALUES (:id,:antes,:despues,:usuario,now())
            """,Map.of("id",id,"antes",before,"despues",after,"usuario",user.id()));
    }
    public static final String ATENCIONES = """
        SELECT a.*,c.numero contrato_numero,c.proveedor_id,p.razon_social proveedor_nombre,
            t.nombre tipo_servicio_nombre,u.nombre unidad_solicitante_nombre,u.provisional unidad_solicitante_provisional,
            s.nombre solicitado_por_nombre,cre.nombre usuario_creacion_nombre
        FROM mod05_atenciontercerizada a JOIN mod05_contrato c ON c.id=a.contrato_id
        JOIN mod05_proveedor p ON p.id=c.proveedor_id JOIN mod05_tiposervicio t ON t.id=a.tipo_servicio_id
        LEFT JOIN mod05_unidadinstitucional u ON u.id=a.unidad_solicitante_id
        JOIN mod05_usuario s ON s.id=a.solicitado_por_id JOIN mod05_usuario cre ON cre.id=a.usuario_creacion_id
        """;
    public Map<String,Object> atencion(long id) { return enrich(one(ATENCIONES+" WHERE a.id=:id",Map.of("id",id))); }
    public Map<String,Object> enrich(Map<String,Object> a) {
        long id=number(a,"id");
        var alcances=rows("""
            SELECT al.id,al.area_verde_id,av.nombre area_verde_nombre,al.ejemplar_id,e.codigo ejemplar_codigo,
                ea.nombre ejemplar_area_verde_nombre,al.metrado,al.unidad_medida_id,
                um.nombre unidad_medida_nombre,um.simbolo unidad_medida_simbolo
            FROM mod05_alcanceatencion al LEFT JOIN mod05_areaverde av ON av.id=al.area_verde_id
            LEFT JOIN mod05_ejemplar e ON e.id=al.ejemplar_id LEFT JOIN mod05_areaverde ea ON ea.id=e.area_verde_id
            LEFT JOIN mod05_unidadmedida um ON um.id=al.unidad_medida_id WHERE al.atencion_id=:id ORDER BY al.id
            """,Map.of("id",id));
        alcances.forEach(al -> { if(al.get("metrado") instanceof BigDecimal decimal) al.put("metrado",decimal.setScale(2).toPlainString()); });
        a.put("alcances",alcances);
        a.put("historial",rows("""
            SELECT h.estado_anterior,h.estado_nuevo,h.usuario_id,u.nombre usuario_nombre,h.fecha_hora
            FROM mod05_historialestado h JOIN mod05_usuario u ON u.id=h.usuario_id
            WHERE h.atencion_id=:id ORDER BY h.fecha_hora,h.id
            """,Map.of("id",id)));
        return a;
    }
    public static final String REPORTES="""
        SELECT r.id,r.atencion_id,r.revision,r.fecha_recepcion,r.resumen,r.usuario_creacion_id,
            u.nombre usuario_creacion_nombre,r.fecha_creacion
        FROM mod05_reporteproveedor r JOIN mod05_usuario u ON u.id=r.usuario_creacion_id
        """;
    public Map<String,Object> reporte(long atencion,long id) {
        return enrichReport(one(REPORTES+" WHERE r.atencion_id=:a AND r.id=:id",Map.of("a",atencion,"id",id)));
    }
    public Map<String,Object> enrichReport(Map<String,Object> reporte) {
        var docs=rows("SELECT id,categoria,alcance_id,descripcion,archivo_id FROM mod05_archivoasociado WHERE reporte_id=:id ORDER BY id",Map.of("id",number(reporte,"id")));
        docs.forEach(d -> {
            long archivo=number(d,"archivo_id"); d.remove("archivo_id");
            d.put("archivo",one("SELECT id,nombre_original,tipo_mime,tamano_bytes,hash_contenido FROM mod05_archivo WHERE id=:id",Map.of("id",archivo)));
        });
        reporte.put("documentos",docs); return reporte;
    }
    public Map<String,Object> archivo(long atencion,long reporte,long documento) {
        return one("""
            SELECT a.* FROM mod05_archivo a JOIN mod05_archivoasociado d ON d.archivo_id=a.id
            JOIN mod05_reporteproveedor r ON r.id=d.reporte_id
            WHERE r.atencion_id=:atencion AND r.id=:reporte AND d.id=:documento
            """,Map.of("atencion",atencion,"reporte",reporte,"documento",documento));
    }
}
