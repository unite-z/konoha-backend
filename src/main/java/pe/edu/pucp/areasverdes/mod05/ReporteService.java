package pe.edu.pucp.areasverdes.mod05;

import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static pe.edu.pucp.areasverdes.mod05.AtencionService.values;
import static pe.edu.pucp.areasverdes.mod05.Mod05Repository.number;

@Service
public class ReporteService {
    final Mod05Repository repo; final AtencionService atenciones; final ArchivoStorage storage; final Clock clock;
    private static final Logger log=LoggerFactory.getLogger(ReporteService.class);
    public ReporteService(Mod05Repository repo,AtencionService atenciones,ArchivoStorage storage,Clock clock) {
        this.repo=repo; this.atenciones=atenciones; this.storage=storage; this.clock=clock;
    }
    @Transactional
    public Map<String,Object> adjuntar(long id,LocalDate fecha,String resumen,List<Entradas.Documento> docs,List<MultipartFile> files,Usuario user) {
        user.exigir(false);
        var antes=repo.lock("atenciontercerizada",id); var a=Atencion.from(antes);
        a.exigir(Atencion.EJECUTADO,Atencion.REPORTE);
        if(fecha.isBefore(a.fin().atZoneSameInstant(Mod05Application.LIMA).toLocalDate())) throw ApiError.invalid("La recepción del informe no puede preceder al fin del servicio");
        if(docs==null||docs.isEmpty()||docs.size()!=files.size()) throw ApiError.invalid("Debe existir un archivo por documento, en el mismo orden");
        if(docs.stream().noneMatch(d -> d.categoria().equals("InformeFinal"))) throw ApiError.invalid("Cada revisión debe incluir un informe final");
        var validated=files.stream().map(storage::validar).toList();
        for(var doc:docs) {
            if(doc.alcance_id()!=null&&number(repo.get("alcanceatencion",doc.alcance_id()),"atencion_id")!=id) throw ApiError.invalid("La evidencia debe corresponder a un alcance de esta atención");
            if(doc.categoria().equals("EvidenciaEspacio")&&doc.alcance_id()==null) throw ApiError.invalid("La evidencia por espacio requiere indicar su alcance");
        }
        List<String> paths=new ArrayList<>();
        // Incluye fallos en el COMMIT, después de retornar de este método.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status!=STATUS_COMMITTED) paths.forEach(path -> {
                    try { storage.borrar(path); } catch(Exception e) { log.error("No se pudo limpiar archivo huérfano {}",path,e); }
                });
            }
        });
        int revision=repo.jdbc().queryForObject("SELECT coalesce(max(revision),0)+1 FROM mod05_reporteproveedor WHERE atencion_id=:id",Map.of("id",id),Integer.class);
        OffsetDateTime now=OffsetDateTime.now(clock);
        long reporte=repo.insert("reporteproveedor",values("atencion_id",id,"revision",revision,"fecha_recepcion",fecha,
            "resumen",AtencionService.text(resumen),"fecha_creacion",now,"fecha_modificacion",now,"usuario_creacion_id",user.id(),"usuario_modificacion_id",user.id()));
        for(int i=0;i<docs.size();i++) {
            var doc=docs.get(i); var file=validated.get(i);
            String key="reportes/"+reporte+"/"+UUID.randomUUID()+"."+file.extension();
            paths.add(key); storage.guardar(key,file);
            long archivo=repo.insert("archivo",values("nombre_original",file.nombre(),"tipo_mime",file.mime(),"tamano_bytes",file.size(),"hash_contenido",file.hash(),"ruta_almacenamiento",key));
            long asociado=repo.insert("archivoasociado",values("reporte_id",reporte,"archivo_id",archivo,"categoria",doc.categoria(),
                "alcance_id",doc.alcance_id(),"descripcion",AtencionService.text(doc.descripcion())));
            repo.audit("ArchivoAsociado",asociado,"Alta",user,Map.of(),repo.get("archivoasociado",asociado),"");
        }
        repo.audit("ReporteProveedor",reporte,"Alta",user,Map.of(),repo.get("reporteproveedor",reporte),"");
        atenciones.guardar(id,values("estado",Atencion.REPORTE),antes,user,"");
        return repo.reporte(id,reporte);
    }
}
