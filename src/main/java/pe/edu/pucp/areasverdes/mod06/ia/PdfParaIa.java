package pe.edu.pucp.areasverdes.mod06.ia;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Prepara un informe PDF para un proveedor de IA que no lee PDF (DeepSeek, AV-395): su texto, si lo
 * tiene, y cada página como imagen JPEG. Con las imágenes también se leen los PDF escaneados.
 */
final class PdfParaIa {

    /** Páginas que se envían como máximo: acota el coste (hasta 1024 tokens por imagen). */
    static final int MAX_PAGINAS = 20;

    /** Resolución de las páginas: legible para tablas y sellos sin pasar del tope por imagen. */
    static final float PPP = 110f;

    record Contenido(String texto, List<ClienteIA.Imagen> paginas, int totalPaginas) {

        /** Texto que acompaña a las imágenes en la petición. */
        String datos() {
            StringBuilder datos =
                    new StringBuilder("Informe del proveedor en PDF (")
                            .append(totalPaginas)
                            .append(totalPaginas == 1 ? " página" : " páginas")
                            .append("). Cada imagen es una página, en orden.");
            if (totalPaginas > paginas.size())
                datos.append(" Solo se envían las ").append(paginas.size()).append(" primeras.");
            if (texto.isBlank())
                datos.append(" El PDF no tiene capa de texto: léelo en las imágenes.");
            else datos.append("\n\nTexto extraído del PDF:\n\n").append(texto.strip());
            return datos.toString();
        }
    }

    private PdfParaIa() {}

    static Contenido convertir(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            int total = documento.getNumberOfPages();
            int enviar = Math.min(total, MAX_PAGINAS);
            PDFTextStripper extractor = new PDFTextStripper();
            extractor.setEndPage(enviar);
            String texto = extractor.getText(documento);
            PDFRenderer renderizador = new PDFRenderer(documento);
            List<ClienteIA.Imagen> paginas = new ArrayList<>();
            for (int i = 0; i < enviar; i++) {
                BufferedImage pagina = renderizador.renderImageWithDPI(i, PPP, ImageType.RGB);
                ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                ImageIO.write(pagina, "jpg", jpeg);
                paginas.add(new ClienteIA.Imagen(jpeg.toByteArray(), "image/jpeg"));
            }
            return new Contenido(texto, paginas, total);
        }
    }
}
