package pe.edu.pucp.areasverdes.mod05;

import java.util.*;
import java.time.LocalDate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import static pe.edu.pucp.areasverdes.mod05.AtencionService.values;

@RestController
@RequestMapping("/api/tercerizados")
@Tag(name="MOD-05")
@SecurityRequirement(name="basicAuth")
public class AtencionController {
    final AtencionService service; final ReporteService reports; final Mod05Repository repo;
    final ConsultasService queries; final ArchivoStorage storage; final ObjectMapper json; final Validator validator;
    public AtencionController(AtencionService service,ReporteService reports,Mod05Repository repo,ConsultasService queries,
                              ArchivoStorage storage,ObjectMapper json,Validator validator) {
        this.service=service; this.reports=reports; this.repo=repo; this.queries=queries; this.storage=storage; this.json=json; this.validator=validator;
    }
    @GetMapping({"/me/","/me"})
    public Map<String,Object> me(@AuthenticationPrincipal Usuario user) { return values("id",user.id(),"usuario",user.usuario(),"nombre",user.nombre(),"rol",user.rol()); }
    @GetMapping({"/atenciones/","/atenciones"})
    public ConsultasService.Pagina list(@AuthenticationPrincipal Usuario user,@RequestParam Map<String,String> params,HttpServletRequest request) {
        user.exigir(false); return queries.atenciones(params,request.getRequestURL().toString());
    }
    @PostMapping({"/atenciones/","/atenciones"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@AuthenticationPrincipal Usuario user,@Valid @RequestBody Entradas.Crear body) { return service.crear(body,user); }
    @GetMapping({"/atenciones/{id}/","/atenciones/{id}"})
    public Map<String,Object> detail(@AuthenticationPrincipal Usuario user,@PathVariable long id) { user.exigir(false); return repo.atencion(id); }
    @PatchMapping({"/atenciones/{id}/orden-compra/","/atenciones/{id}/orden-compra"})
    public Map<String,Object> orden(@AuthenticationPrincipal Usuario user,@PathVariable long id,@Valid @RequestBody Entradas.Orden body) { return service.orden(id,body,user); }
    @PostMapping({"/atenciones/{id}/inicio/","/atenciones/{id}/inicio"})
    public Map<String,Object> inicio(@AuthenticationPrincipal Usuario user,@PathVariable long id,@Valid @RequestBody Entradas.Inicio body) { return service.inicio(id,body,user); }
    @PostMapping({"/atenciones/{id}/fin/","/atenciones/{id}/fin"})
    public Map<String,Object> fin(@AuthenticationPrincipal Usuario user,@PathVariable long id,@Valid @RequestBody Entradas.Fin body) { return service.fin(id,body,user); }
    @PatchMapping({"/atenciones/{id}/origen/","/atenciones/{id}/origen"})
    public Map<String,Object> origen(@AuthenticationPrincipal Usuario user,@PathVariable long id,@Valid @RequestBody Entradas.Origen body) { return service.origen(id,body,user); }
    @PatchMapping({"/atenciones/{id}/acuerdo-unidad/","/atenciones/{id}/acuerdo-unidad"})
    public Map<String,Object> acuerdo(@AuthenticationPrincipal Usuario user,@PathVariable long id,@Valid @RequestBody Entradas.Acuerdo body) { return service.acuerdo(id,body,user); }
    @GetMapping({"/atenciones/{id}/reportes/","/atenciones/{id}/reportes"})
    public ConsultasService.Pagina reports(@AuthenticationPrincipal Usuario user,@PathVariable long id,@RequestParam Map<String,String> params,HttpServletRequest request) {
        user.exigir(false); return queries.reportes(id,params,request.getRequestURL().toString());
    }
    @PostMapping(value={"/atenciones/{id}/reportes/","/atenciones/{id}/reportes"},consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> upload(@AuthenticationPrincipal Usuario user,@PathVariable long id,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha_recepcion,
        @RequestParam(required=false) String resumen,@RequestParam String documentos,@RequestPart List<MultipartFile> archivos,
        HttpServletRequest request) {
        user.exigir(false);
        var fields=new HashSet<>(request.getParameterMap().keySet());
        fields.removeAll(Set.of("fecha_recepcion","resumen","documentos"));
        if(!fields.isEmpty()) throw ApiError.invalid("Campos no admitidos: "+fields);
        List<Entradas.Documento> docs;
        try { docs=json.readValue(documentos,new TypeReference<>() {}); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw ApiError.invalid("documentos debe ser una lista JSON válida"); }
        if(docs==null||docs.isEmpty()||docs.stream().anyMatch(d -> d==null||!validator.validate(d).isEmpty())) throw ApiError.invalid("Metadatos de documentos incompletos o inválidos");
        return reports.adjuntar(id,fecha_recepcion,resumen,docs,archivos,user);
    }
    @GetMapping({"/atenciones/{id}/reportes/{reporte}/","/atenciones/{id}/reportes/{reporte}"})
    public Map<String,Object> report(@AuthenticationPrincipal Usuario user,@PathVariable long id,@PathVariable long reporte) {
        user.exigir(false); return repo.reporte(id,reporte);
    }
    @GetMapping({"/atenciones/{id}/reportes/{reporte}/documentos/{documento}/descarga/","/atenciones/{id}/reportes/{reporte}/documentos/{documento}/descarga"})
    public ResponseEntity<Resource> download(@AuthenticationPrincipal Usuario user,@PathVariable long id,@PathVariable long reporte,@PathVariable long documento) {
        user.exigir(false); var file=repo.archivo(id,reporte,documento);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)file.get("tipo_mime")))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename((String)file.get("nombre_original"),java.nio.charset.StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff")
            .body(storage.leer((String)file.get("ruta_almacenamiento")));
    }
    @GetMapping({"/catalogos/{catalogo}/","/catalogos/{catalogo}"})
    public ConsultasService.Pagina catalog(@AuthenticationPrincipal Usuario user,@PathVariable String catalogo,@RequestParam Map<String,String> params,HttpServletRequest request) {
        user.exigir(false); return queries.catalogo(catalogo,params,request.getRequestURL().toString());
    }
}
