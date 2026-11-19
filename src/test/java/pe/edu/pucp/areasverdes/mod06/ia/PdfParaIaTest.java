package pe.edu.pucp.areasverdes.mod06.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ServicioIANoDisponible;

/** Informes PDF para DeepSeek: texto e imagen por página (AV-395). */
class PdfParaIaTest {

    /** PDF con {@code paginas} páginas; la primera lleva {@code texto} si no es nulo. */
    static byte[] pdf(int paginas, String texto) throws IOException {
        try (PDDocument documento = new PDDocument()) {
            for (int i = 0; i < paginas; i++) documento.addPage(new PDPage());
            if (texto != null) {
                try (PDPageContentStream contenido =
                        new PDPageContentStream(documento, documento.getPage(0))) {
                    contenido.beginText();
                    contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contenido.newLineAtOffset(72, 700);
                    contenido.showText(texto);
                    contenido.endText();
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    @Test
    void sacaElTextoYUnaImagenJpegPorPagina() throws IOException {
        PdfParaIa.Contenido contenido = PdfParaIa.convertir(pdf(1, "Metrado 1250 m2"));
        assertEquals(1, contenido.paginas().size());
        byte[] jpeg = contenido.paginas().get(0).datos();
        assertTrue((jpeg[0] & 0xFF) == 0xFF && (jpeg[1] & 0xFF) == 0xD8, "JPEG");
        assertEquals("image/jpeg", contenido.paginas().get(0).tipoMime());
        assertTrue(
                contenido.datos().contains("(1 página)")
                        && contenido.datos().contains("Metrado 1250 m2"));
    }

    @Test
    void unEscaneoSinTextoSeLeeEnLasImagenesYSeAcotanLasPaginas() throws IOException {
        PdfParaIa.Contenido contenido = PdfParaIa.convertir(pdf(PdfParaIa.MAX_PAGINAS + 2, null));
        assertEquals(PdfParaIa.MAX_PAGINAS, contenido.paginas().size());
        assertTrue(contenido.datos().contains("Solo se envían las 20 primeras"));
        assertTrue(contenido.datos().contains("no tiene capa de texto"));
    }

    @Test
    void deepSeekRecibeLasPaginasComoImagenesYUnPdfDanadoDa503() throws Exception {
        List<String> enviados = new ArrayList<>();
        ClienteDeepSeek cliente =
                new ClienteDeepSeek(
                        "clave",
                        "deepseek-flash",
                        cuerpo -> {
                            enviados.add(cuerpo);
                            return "{\"choices\": [{\"message\": {\"content\": \"{\\\"campo_1\\\":"
                                    + " 1250}\"}}]}";
                        });
        Map<String, Object> datos = cliente.extraerJson(pdf(2, "Metrado 1250"), Map.of(), "Extrae");
        assertEquals(1250, ((Number) datos.get("campo_1")).intValue());
        assertTrue(
                enviados.get(0).contains("data:image/jpeg;base64,")
                        && enviados.get(0).contains("Metrado 1250"));

        ServicioIANoDisponible e =
                assertThrows(
                        ServicioIANoDisponible.class,
                        () -> cliente.extraerJson(new byte[] {1, 2, 3}, Map.of(), "Extrae"));
        assertEquals(ClienteGemini.MENSAJE_IA_FALLO, e.getMessage());
    }
}
