package pe.edu.pucp.areasverdes.mod05;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.security.*;
import java.util.zip.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;

@Component
public class ArchivoStorage {
    final Path root; final long maxBytes;
    public ArchivoStorage(@Value("${mod05.archivos}") String root,@Value("${mod05.max-archivo-bytes}") long maxBytes) {
        this.root=Path.of(root).toAbsolutePath().normalize(); this.maxBytes=maxBytes;
    }
    public record Validado(String nombre,String mime,long size,String hash,String extension,byte[] bytes) {}
    static final Map<String,String> MIME=Map.of("pdf","application/pdf","jpg","image/jpeg","jpeg","image/jpeg","png","image/png",
        "docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    public Validado validar(MultipartFile file) {
        String name=Optional.ofNullable(file.getOriginalFilename()).orElse("").replace('\\','/');
        name=name.substring(name.lastIndexOf('/')+1);
        String extension=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
        if(name.isBlank()||name.length()>200||!MIME.containsKey(extension)||name.chars().anyMatch(Character::isISOControl))
            throw ApiError.invalid("Archivo no admitido. Usa PDF, JPEG, PNG, DOCX o XLSX");
        if(file.getSize()<=0||file.getSize()>maxBytes) throw ApiError.invalid("El archivo está vacío o supera el tamaño máximo permitido");
        try {
            byte[] bytes=file.getBytes();
            boolean valid=switch(extension) {
                case "pdf" -> starts(bytes,new byte[]{37,80,68,70,45});
                case "jpg","jpeg" -> starts(bytes,new byte[]{(byte)255,(byte)216,(byte)255});
                case "png" -> starts(bytes,new byte[]{(byte)137,80,78,71,13,10,26,10});
                case "docx","xlsx" -> office(bytes,extension);
                default -> false;
            };
            if(!valid) throw ApiError.invalid("El contenido del archivo no corresponde a su extensión");
            return new Validado(name,MIME.get(extension),bytes.length,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),extension,bytes);
        } catch(IOException e) { throw ApiError.invalid("No se pudo leer el archivo o su estructura es inválida"); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    boolean starts(byte[] bytes,byte[] prefix) { return bytes.length>=prefix.length&&Arrays.equals(Arrays.copyOf(bytes,prefix.length),prefix); }
    boolean office(byte[] bytes,String extension) throws IOException {
        boolean content=false,main=false; long size=0; int entries=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry; byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null) {
                if(++entries>10000) return false;
                content|=entry.getName().equals("[Content_Types].xml");
                main|=entry.getName().equals(extension.equals("docx")?"word/document.xml":"xl/workbook.xml");
                int n; while((n=zip.read(buffer))!=-1) { size+=n; if(size>100*1024*1024) return false; }
            }
        }
        return content&&main;
    }
    Path path(String key) {
        Path p=root.resolve(key).normalize();
        if(!p.startsWith(root)||p.equals(root)) throw ApiError.invalid("Ruta de archivo inválida");
        return p;
    }
    public void guardar(String key,Validado file) {
        try { Path target=path(key); Files.createDirectories(target.getParent()); Files.write(target,file.bytes(),StandardOpenOption.CREATE_NEW); }
        catch(IOException e) { throw ApiError.storage(); }
    }
    public void borrar(String key) throws IOException { Files.deleteIfExists(path(key)); }
    public Resource leer(String key) {
        Path file=path(key);
        if(!Files.exists(file)) throw ApiError.missing("El archivo no está disponible en el almacenamiento");
        if(!Files.isReadable(file)) throw ApiError.storage();
        return new FileSystemResource(file);
    }
}
