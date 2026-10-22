package pe.edu.pucp.areasverdes.mod05;

import java.time.*;
import java.util.*;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ConsultasService {
    final Mod05Repository repo; final Clock clock;
    public ConsultasService(Mod05Repository repo,Clock clock) { this.repo=repo; this.clock=clock; }
    public record Pagina(long count,String next,String previous,List<Map<String,Object>> results) {}
    record Catalogo(String sql,String order,List<String> search,Map<String,String> filters) {}
    static final Map<String,Catalogo> CATALOGOS=Map.of(
        "proveedores", new Catalogo("SELECT id,razon_social,activo FROM mod05_proveedor","razon_social,id",List.of("razon_social"),Map.of("activo","bool")),
        "contratos", new Catalogo("""
            SELECT c.*,p.razon_social proveedor_nombre,t.nombre tipo_servicio_nombre FROM mod05_contrato c
            JOIN mod05_proveedor p ON p.id=c.proveedor_id JOIN mod05_tiposervicio t ON t.id=c.tipo_servicio_id
            ""","numero,id",List.of("numero","proveedor_nombre","tipo_servicio_nombre"),Map.of("proveedor_id","id","tipo_servicio_id","id","vigente","vigente")),
        "tipos-servicio",new Catalogo("SELECT * FROM mod05_tiposervicio","nombre,id",List.of("nombre"),Map.of("activo","bool","es_riesgo","bool","ejecutor","ejecutor")),
        "areas-verdes",new Catalogo("SELECT * FROM mod05_areaverde","nombre,id",List.of("nombre"),Map.of("activo","bool")),
        "ejemplares",new Catalogo("SELECT e.*,a.nombre area_verde_nombre FROM mod05_ejemplar e LEFT JOIN mod05_areaverde a ON a.id=e.area_verde_id","codigo,id",List.of("codigo","area_verde_nombre"),Map.of("area_verde_id","id")),
        "unidades-medida",new Catalogo("SELECT * FROM mod05_unidadmedida","nombre,id",List.of("nombre","simbolo"),Map.of()),
        "unidades-institucionales",new Catalogo("SELECT * FROM mod05_unidadinstitucional","nombre,id",List.of("nombre"),Map.of("activo","bool","provisional","bool"))
    );
    public Pagina catalogo(String name,Map<String,String> params,String url) {
        var config=CATALOGOS.get(name);
        if(config==null) throw ApiError.missing("Catálogo inexistente");
        return list(config,params,url,UnaryOperator.identity());
    }
    public Pagina atenciones(Map<String,String> params,String url) {
        return list(new Catalogo(Mod05Repository.ATENCIONES,"fecha_creacion DESC,id DESC",
            List.of("codigo","descripcion","numero_orden_compra","contrato_numero","proveedor_nombre","tipo_servicio_nombre","unidad_solicitante_nombre"),
            Map.of("estado","estado","origen","origen","contrato_id","id")),params,url,repo::enrich);
    }
    public Pagina reportes(long id,Map<String,String> params,String url) {
        repo.get("atenciontercerizada",id);
        return list(new Catalogo(Mod05Repository.REPORTES+" WHERE r.atencion_id="+id,"revision DESC",List.of(),Map.of()),params,url,repo::enrichReport);
    }
    Pagina list(Catalogo config,Map<String,String> query,String url,UnaryOperator<Map<String,Object>> enrich) {
        Map<String,Object> args=new HashMap<>();
        StringBuilder where=new StringBuilder(" WHERE true");
        String search=query.getOrDefault("q","").strip();
        if(search.length()>100) throw ApiError.invalid("La búsqueda admite hasta 100 caracteres");
        if(!search.isEmpty()&&!config.search().isEmpty()) {
            int n=0;
            for(String term: search.split("\\s+")) {
                String key="q"+n++;
                // POSITION trata %, _ y comillas como texto literal, no comodines SQL.
                where.append(" AND (").append(String.join(" OR ",config.search().stream()
                    .map(c -> "position(lower(:"+key+") in lower(coalesce("+c+",'')))>0").toList())).append(")");
                args.put(key,term); if(n==8) break;
            }
        }
        config.filters().forEach((field,type) -> {
            String text=query.getOrDefault(field,"").strip(); if(text.isEmpty()) return;
            if(type.equals("vigente")) {
                boolean vigente=bool(text); args.put("hoy",LocalDate.now(clock));
                where.append(vigente?" AND ":" AND NOT ").append("(vigencia_inicio<=:hoy AND vigencia_fin>=:hoy)");
            } else {
                Object value=switch(type) {
                    case "id" -> positive(text);
                    case "bool" -> bool(text);
                    case "estado" -> option(text,List.of(Atencion.REQUERIMIENTO,Atencion.OC,Atencion.EJECUCION,Atencion.EJECUTADO,Atencion.REPORTE));
                    case "origen" -> option(text,List.of("Demanda","ProgramacionEstacional","SolicitudUnidad"));
                    case "ejecutor" -> option(text,List.of("Tercerizado","PersonalPropio"));
                    default -> throw new IllegalStateException();
                };
                where.append(" AND ").append(field).append("=:").append(field); args.put(field,value);
            }
        });
        String base=" FROM ("+config.sql()+") datos"+where;
        long count=repo.jdbc().queryForObject("SELECT count(*)"+base,args,Long.class);
        long totalPages=Math.max(1,(count+24)/25);
        String requested=query.getOrDefault("page","1");
        long page;
        try { page=requested.equals("last")?totalPages:Long.parseLong(requested); }
        catch(NumberFormatException e) { throw ApiError.missing("Página inválida"); }
        if(page<1||page>totalPages) throw ApiError.missing("Página inexistente");
        args.put("offset",(page-1)*25);
        var results=repo.rows("SELECT *"+base+" ORDER BY "+config.order()+" LIMIT 25 OFFSET :offset",args).stream().map(enrich).toList();
        return new Pagina(count,page<totalPages?link(url,query,page+1):null,page>1?link(url,query,page-1):null,results);
    }
    String link(String url,Map<String,String> query,long page) {
        var builder=UriComponentsBuilder.fromUriString(url);
        query.forEach((k,v)-> { if(!k.equals("page")) builder.queryParam(k,v); });
        return builder.queryParam("page",page).build().encode().toUriString();
    }
    static long positive(String s) {
        try { long n=Long.parseLong(s); if(n>0) return n; } catch(NumberFormatException ignored) {}
        throw ApiError.invalid("Indica un número entero positivo");
    }
    static boolean bool(String s) {
        return switch(s.toLowerCase(Locale.ROOT)) {
            case "true","1" -> true; case "false","0" -> false;
            default -> throw ApiError.invalid("Usa true o false");
        };
    }
    static String option(String value,List<String> allowed) {
        if(!allowed.contains(value)) throw ApiError.invalid("Valor no válido. Opciones: "+String.join(", ",allowed));
        return value;
    }
}
