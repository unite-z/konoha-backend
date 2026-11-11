package pe.edu.pucp.areasverdes.mod06.gestion.exportaciones;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

/**
 * Escribe una {@link Tabla} en Excel (fastexcel, como MOD-01) o en PDF (OpenPDF), en memoria. No
 * guarda nada: {@code ExportacionReportesService} decide dónde.
 */
public final class GeneradorArchivos {

    /** Lo que se exporta: título, subtítulo con los parámetros, columnas y filas en texto. */
    public record Tabla(
            String titulo, String subtitulo, List<String> columnas, List<List<String>> filas) {}

    public static final String MIME_PDF = "application/pdf";
    public static final String MIME_EXCEL =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private GeneradorArchivos() {}

    public static byte[] excel(Tabla tabla) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (Workbook libro = new Workbook(salida, "Areas Verdes", "1.0")) {
            Worksheet hoja = libro.newWorksheet(nombreHoja(tabla.titulo()));
            hoja.value(0, 0, tabla.titulo());
            hoja.style(0, 0).bold().set();
            hoja.value(1, 0, tabla.subtitulo());
            for (int c = 0; c < tabla.columnas().size(); c++) {
                hoja.value(3, c, tabla.columnas().get(c));
                hoja.style(3, c).bold().fillColor("E2EFDA").set();
            }
            for (int f = 0; f < tabla.filas().size(); f++) {
                List<String> fila = tabla.filas().get(f);
                for (int c = 0; c < fila.size(); c++) {
                    if (fila.get(c) != null) hoja.value(4 + f, c, fila.get(c));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return salida.toByteArray();
    }

    public static byte[] pdf(Tabla tabla) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();
            documento.add(
                    new Paragraph(
                            tabla.titulo(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            documento.add(
                    new Paragraph(
                            tabla.subtitulo(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
            documento.add(new Paragraph(" "));
            PdfPTable t = new PdfPTable(tabla.columnas().size());
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            Font cabecera = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            Font celda = FontFactory.getFont(FontFactory.HELVETICA, 8);
            for (String columna : tabla.columnas()) {
                PdfPCell c = new PdfPCell(new Phrase(columna, cabecera));
                c.setBackgroundColor(new Color(0xE2, 0xEF, 0xDA));
                c.setHorizontalAlignment(Element.ALIGN_LEFT);
                t.addCell(c);
            }
            for (List<String> fila : tabla.filas()) {
                for (int c = 0; c < tabla.columnas().size(); c++) {
                    String valor = c < fila.size() && fila.get(c) != null ? fila.get(c) : "";
                    t.addCell(new PdfPCell(new Phrase(valor, celda)));
                }
            }
            documento.add(t);
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        } finally {
            documento.close();
        }
        return salida.toByteArray();
    }

    /** Excel limita el nombre de la hoja a 31 caracteres y prohíbe algunos signos. */
    private static String nombreHoja(String titulo) {
        String limpio = titulo.replaceAll("[\\\\/?*\\[\\]:]", " ").strip();
        return limpio.length() <= 31 ? limpio : limpio.substring(0, 31);
    }
}
