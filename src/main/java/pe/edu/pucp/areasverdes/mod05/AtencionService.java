package pe.edu.pucp.areasverdes.mod05;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static pe.edu.pucp.areasverdes.mod05.Atencion.*;
import static pe.edu.pucp.areasverdes.mod05.Mod05Repository.number;

@Service
public class AtencionService {
    final Mod05Repository repo; final Clock clock;
    public AtencionService(Mod05Repository repo,Clock clock) { this.repo=repo; this.clock=clock; }
    public static Map<String,Object> values(Object... entries) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(int i=0;i<entries.length;i+=2) result.put((String)entries[i],entries[i+1]);
        return result;
    }
    static String text(String s) { return s==null?"":s.strip(); }
    OffsetDateTime now() { return OffsetDateTime.now(clock); }
    Long unidad(String origen,Long id,String nombre,Usuario user) {
        nombre=text(nombre);
        if(!origen.equals("SolicitudUnidad")) {
            if(id!=null||!nombre.isEmpty()) throw ApiError.invalid("Solo SolicitudUnidad admite una unidad solicitante");
            return null;
        }
        if((id==null)==nombre.isEmpty()) throw ApiError.invalid("Indica una unidad existente o un nombre de unidad provisional");
        if(id!=null) {
            if(!(boolean)repo.get("unidadinstitucional",id).get("activo")) throw ApiError.invalid("La unidad está inactiva");
            return id;
        }
        var existing=repo.rows("INSERT INTO mod05_unidadinstitucional(nombre,provisional,activo) VALUES (:nombre,true,true) ON CONFLICT(nombre) DO NOTHING RETURNING id",Map.of("nombre",nombre));
        var u=repo.one("SELECT * FROM mod05_unidadinstitucional WHERE nombre=:nombre",Map.of("nombre",nombre));
        if(!(boolean)u.get("activo")) throw ApiError.invalid("La unidad está inactiva");
        if(!existing.isEmpty()) repo.audit("UnidadInstitucional",number(u,"id"),"Alta",user,Map.of(),u,"");
        return number(u,"id");
    }
    @Transactional
    public Map<String,Object> crear(Entradas.Crear input,Usuario user) {
        user.exigir(true);
        var c=repo.lock("contrato",input.contrato_id());
        var proveedor=repo.get("proveedor",number(c,"proveedor_id"));
        var servicio=repo.get("tiposervicio",number(c,"tipo_servicio_id"));
        LocalDate hoy=LocalDate.now(clock);
        if(hoy.isBefore((LocalDate)c.get("vigencia_inicio"))||hoy.isAfter((LocalDate)c.get("vigencia_fin")))
            throw ApiError.invalid("El contrato no está vigente en la fecha de registro");
        if(!(boolean)proveedor.get("activo")||!(boolean)servicio.get("activo")) throw ApiError.invalid("Proveedor o tipo de servicio inactivo");
        if(!servicio.get("ejecutor").equals("Tercerizado")) throw ApiError.invalid("El contrato debe corresponder a un servicio tercerizado");
        Long unidad=unidad(input.origen(),input.unidad_solicitante_id(),input.unidad_provisional(),user);
        Set<String> destinos=new HashSet<>();
        for(var a:input.alcances()) {
            if((a.area_verde_id()==null)==(a.ejemplar_id()==null)) throw ApiError.invalid("Cada alcance debe indicar solo un área verde o un ejemplar");
            String key=a.area_verde_id()!=null?"area:"+a.area_verde_id():"ejemplar:"+a.ejemplar_id();
            if(!destinos.add(key)) throw ApiError.invalid("No repitas un espacio o ejemplar en la misma atención");
            if(a.area_verde_id()!=null) {
                if(!(boolean)repo.get("areaverde",a.area_verde_id()).get("activo")) throw ApiError.invalid("El área verde está inactiva");
            } else repo.get("ejemplar",a.ejemplar_id());
            if((a.metrado()==null)!=(a.unidad_medida_id()==null)) throw ApiError.invalid("Metrado y unidad deben registrarse juntos");
            if(a.unidad_medida_id()!=null) repo.get("unidadmedida",a.unidad_medida_id());
        }
        var data=values("codigo","AT-"+UUID.randomUUID().toString().replace("-",""),"clase","ProgramadaContrato",
            "contrato_id",input.contrato_id(),"tipo_servicio_id",number(c,"tipo_servicio_id"),"descripcion",input.descripcion().strip(),
            "origen",input.origen(),"unidad_solicitante_id",unidad,"solicitado_por_id",user.id(),
            "numero_orden_compra","","estado",REQUERIMIENTO,"es_riesgo",Boolean.TRUE.equals(input.es_riesgo())||(boolean)servicio.get("es_riesgo"),
            "comentarios_acuerdo_unidad","","fecha_creacion",now(),"fecha_modificacion",now(),
            "usuario_creacion_id",user.id(),"usuario_modificacion_id",user.id());
        long id=repo.insert("atenciontercerizada",data);
        for(var a:input.alcances()) {
            long alcance=repo.insert("alcanceatencion",values("atencion_id",id,"area_verde_id",a.area_verde_id(),
                "ejemplar_id",a.ejemplar_id(),"metrado",a.metrado(),"unidad_medida_id",a.unidad_medida_id()));
            repo.audit("AlcanceAtencion",alcance,"Alta",user,Map.of(),repo.get("alcanceatencion",alcance),"");
        }
        repo.audit("AtencionTercerizada",id,"Alta",user,Map.of(),repo.get("atenciontercerizada",id),"");
        repo.history(id,"",REQUERIMIENTO,user);
        return repo.atencion(id);
    }
    Map<String,Object> guardar(long id,Map<String,Object> cambios,Map<String,Object> antes,Usuario user,String motivo) {
        cambios.put("fecha_modificacion",now()); cambios.put("usuario_modificacion_id",user.id());
        repo.update("atenciontercerizada",id,cambios);
        var despues=repo.get("atenciontercerizada",id);
        repo.audit("AtencionTercerizada",id,"Modificacion",user,antes,despues,motivo);
        repo.history(id,(String)antes.get("estado"),(String)despues.get("estado"),user);
        return repo.atencion(id);
    }
    @Transactional
    public Map<String,Object> orden(long id,Entradas.Orden input,Usuario user) {
        user.exigir(false); var antes=repo.lock("atenciontercerizada",id); var a=Atencion.from(antes);
        a.exigir(REQUERIMIENTO,OC);
        if(!a.ordenCompra().isBlank()&&text(input.motivo()).isEmpty()) throw ApiError.invalid("Indica el motivo de corrección de la OC");
        return guardar(id,values("numero_orden_compra",input.numero_orden_compra().strip(),"fecha_orden_compra",now(),"estado",OC),antes,user,text(input.motivo()));
    }
    @Transactional
    public Map<String,Object> inicio(long id,Entradas.Inicio input,Usuario user) {
        user.exigir(false); var antes=repo.lock("atenciontercerizada",id); var a=Atencion.from(antes); a.exigir(OC);
        if(a.esRiesgo()) throw ApiError.conflict("Atención de riesgo: falta integrar la validación de seguridad; no se permite iniciar");
        return guardar(id,values("fecha_hora_inicio",input.fecha_hora_inicio(),"estado",EJECUCION),antes,user,"");
    }
    @Transactional
    public Map<String,Object> fin(long id,Entradas.Fin input,Usuario user) {
        user.exigir(false); var antes=repo.lock("atenciontercerizada",id); var a=Atencion.from(antes); a.exigir(EJECUCION);
        if(input.fecha_hora_fin().isBefore(a.inicio())) throw ApiError.invalid("La fecha de fin no puede ser anterior al inicio");
        return guardar(id,values("fecha_hora_fin",input.fecha_hora_fin(),"estado",EJECUTADO),antes,user,"");
    }
    @Transactional
    public Map<String,Object> origen(long id,Entradas.Origen input,Usuario user) {
        user.exigir(true); var antes=repo.lock("atenciontercerizada",id); Atencion.from(antes).exigir(REQUERIMIENTO,OC);
        Long unidad=unidad(input.origen(),input.unidad_solicitante_id(),input.unidad_provisional(),user);
        return guardar(id,values("origen",input.origen(),"unidad_solicitante_id",unidad),antes,user,input.motivo().strip());
    }
    @Transactional
    public Map<String,Object> acuerdo(long id,Entradas.Acuerdo input,Usuario user) {
        user.exigir(true); var antes=repo.lock("atenciontercerizada",id); var a=Atencion.from(antes); a.exigir(EJECUTADO,REPORTE);
        if(!a.origen().equals("SolicitudUnidad")) throw ApiError.invalid("El acuerdo corresponde únicamente a solicitudes de unidad");
        if((input.acuerdo_unidad()==null)!=(input.fecha_acuerdo_unidad()==null)) throw ApiError.invalid("El acuerdo comunicado requiere fecha; el pendiente no lleva fecha");
        if(input.fecha_acuerdo_unidad()!=null&&input.fecha_acuerdo_unidad().isBefore(a.fin().atZoneSameInstant(Mod05Application.LIMA).toLocalDate()))
            throw ApiError.invalid("El acuerdo no puede ser anterior a la finalización");
        return guardar(id,values("acuerdo_unidad",input.acuerdo_unidad(),"fecha_acuerdo_unidad",input.fecha_acuerdo_unidad(),
            "comentarios_acuerdo_unidad",text(input.comentarios_acuerdo_unidad())),antes,user,"");
    }
}
