package pe.edu.pucp.areasverdes.mod05;

import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class ArchivoStorageTest {
    @TempDir Path temp;
    @ParameterizedTest @ValueSource(strings={"pdf","jpg","jpeg","png","docx","xlsx"})
    void formatosPermitidos(String extension) throws Exception {
        byte[] bytes=switch(extension) {
            case "pdf" -> "%PDF-1.7 ejemplo".getBytes();
            case "jpg","jpeg" -> new byte[]{(byte)255,(byte)216,(byte)255,0};
            case "png" -> new byte[]{(byte)137,80,78,71,13,10,26,10};
            default -> office(extension);
        };
        var storage=new ArchivoStorage(temp.toString(),1024);
        var file=storage.validar(new MockMultipartFile("archivos","archivo."+extension,"application/octet-stream",bytes));
        assertEquals(64,file.hash().length());
        storage.guardar("reportes/1/nuevo."+extension,file);
        assertArrayEquals(bytes,storage.leer("reportes/1/nuevo."+extension).getContentAsByteArray());
        assertThrows(ApiError.class,()->storage.guardar("reportes/1/nuevo."+extension,file)); // No sobrescribe.
    }
    static byte[] office(String extension) throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)) {
            for(String name:new String[]{"[Content_Types].xml",extension.equals("docx")?"word/document.xml":"xl/workbook.xml"}) {
                zip.putNextEntry(new ZipEntry(name)); zip.write("<xml/>".getBytes()); zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    @ParameterizedTest @ValueSource(strings={"exe","pdf","docx","xlsx","png","jpg"})
    void contenidoInvalido(String extension) {
        var storage=new ArchivoStorage(temp.toString(),1024);
        assertThrows(ApiError.class,()->storage.validar(new MockMultipartFile("archivos","archivo."+extension,"application/pdf","falso".getBytes())));
    }
    @Test void falloDiscoYLimpieza() throws Exception {
        Path occupied=temp.resolve("ocupado"); Files.writeString(occupied,"un archivo no es un directorio");
        var storage=new ArchivoStorage(occupied.toString(),1024);
        var file=storage.validar(new MockMultipartFile("archivos","archivo.pdf","application/pdf","%PDF-1.7".getBytes()));
        assertEquals(503,assertThrows(ApiError.class,()->storage.guardar("reporte.pdf",file)).status);
        assertThrows(ApiError.class,()->storage.leer("../externo"));
    }
    @Test void tamanoYNombre() {
        var storage=new ArchivoStorage(temp.toString(),5);
        assertThrows(ApiError.class,()->storage.validar(new MockMultipartFile("archivos","a.pdf","application/pdf",new byte[0])));
        assertThrows(ApiError.class,()->storage.validar(new MockMultipartFile("archivos","a.pdf","application/pdf","%PDF-1.7".getBytes())));
    }
}
