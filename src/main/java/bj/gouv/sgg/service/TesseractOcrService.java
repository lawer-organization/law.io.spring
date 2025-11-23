package bj.gouv.sgg.service;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.exception.TesseractInitializationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.tesseract.TessBaseAPI;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.bytedeco.leptonica.global.leptonica.*;

/**
 * Service d'extraction OCR avec Tesseract
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TesseractOcrService {
    
    private final LawProperties properties;
    
    // Répertoire temporaire pour tessdata (extrait une seule fois)
    private static Path tessdataDir;
    
    /**
     * Perform OCR on PDF file and write result to text file
     */
    public void performOcr(File pdfFile, File ocrFile) throws IOException {
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());
        String text = extractText(pdfBytes);
        
        // Create parent directory if needed
        File parentDir = ocrFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        Files.writeString(ocrFile.toPath(), text);
        log.info("OCR completed: {} -> {} ({} chars)", pdfFile.getName(), ocrFile.getName(), text.length());
    }
    
    public String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            // Try direct extraction first
            PDFTextStripper stripper = new PDFTextStripper();
            String directText = stripper.getText(document);
            
            double quality = calculateTextQuality(directText);
            
            if (quality >= properties.getOcr().getQualityThreshold()) {
                log.debug("Direct extraction OK (quality: {:.2f})", quality);
                return directText;
            }
            
            log.debug("Direct extraction quality too low ({:.2f}), using OCR", quality);
            return extractWithOcr(document);
        }
    }
    
    /**
     * Extrait et prépare les données Tesseract depuis les resources.
     * Les fichiers .traineddata doivent être dans src/main/resources/tessdata/
     */
    private static synchronized Path extractTessdata() throws IOException {
        if (tessdataDir == null) {
            tessdataDir = Files.createTempDirectory("tessdata");
            String[] files = { "fra.traineddata" }; // Ajouter d'autres langues si nécessaire
            
            for (String file : files) {
                try (InputStream is = TesseractOcrService.class.getResourceAsStream("/tessdata/" + file)) {
                    if (is != null) {
                        Path targetFile = tessdataDir.resolve(file);
                        Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        log.info("tesseract-tessdata: extracted {}", file);
                    } else {
                        log.warn("tesseract-tessdata: file not found in resources: {}", file);
                    }
                }
            }
        }
        return tessdataDir;
    }
    
    private String extractWithOcr(PDDocument document) throws IOException {
        StringBuilder result = new StringBuilder();
        PDFRenderer renderer = new PDFRenderer(document);
        int totalPages = document.getNumberOfPages();
        
        log.info("tesseract-pages: pages={}", totalPages);
        
        // Extraire tessdata dans un répertoire temporaire
        Path tessDir = extractTessdata();
        
        try (TessBaseAPI api = new TessBaseAPI()) {
            // Retry initialization if needed
            int retries = 0;
            int maxRetries = 3;
            while (retries < maxRetries) {
                // Initialiser avec le chemin du répertoire tessdata
                if (api.Init(tessDir.toString(), properties.getOcr().getLanguage()) == 0) {
                    break;
                }
                retries++;
                if (retries < maxRetries) {
                    log.warn("tesseract-retry: attempt={}/{}", retries, maxRetries);
                    try {
                        Thread.sleep(1000L * retries);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new TesseractInitializationException(tessDir.toString(), "Tesseract initialization interrupted", e);
                    }
                } else {
                    throw new TesseractInitializationException(tessDir.toString(), maxRetries);
                }
            }
            
            for (int page = 0; page < totalPages; page++) {
                String pageText = processPage(api, renderer, page);
                
                if (pageText != null && !pageText.isBlank()) {
                    if (totalPages > 1) {
                        result.append("\n\n=== Page ").append(page + 1).append("/").append(totalPages).append(" ===\n\n");
                    }
                    result.append(pageText);
                    
                    // Vérifier si AMPLIATIONS est détecté (fin de la loi)
                    if (pageText.toUpperCase().contains("AMPLIATIONS")) {
                        log.info("tesseract-ampliations-detected: page={}/{} (stopping OCR)", page + 1, totalPages);
                        break; // Arrêter l'OCR immédiatement
                    }
                }
                
                // Log de progression pour les documents longs
                if ((page + 1) % 10 == 0 || page == totalPages - 1) {
                    log.info("tesseract-progress: page={}/{}", page + 1, totalPages);
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Traite une page PDF individuelle : conversion en image puis OCR
     */
    private String processPage(TessBaseAPI api, PDFRenderer renderer, int pageIndex) throws IOException {
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, properties.getOcr().getDpi());
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();
        
        PIX pix = pixReadMem(imageBytes, imageBytes.length);
        if (pix == null) {
            log.warn("Failed to read image for page {}", pageIndex);
            return "";
        }
        
        try {
            api.SetImage(pix);
            BytePointer textPtr = api.GetUTF8Text();
            if (textPtr != null) {
                try {
                    return textPtr.getString(StandardCharsets.UTF_8);
                } finally {
                    textPtr.deallocate();
                }
            }
            return "";
        } finally {
            pixDestroy(pix);
        }
    }
    
    private double calculateTextQuality(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }
        
        int totalChars = text.length();
        int validChars = 0;
        int spaces = 0;
        
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                validChars++;
            } else if (Character.isWhitespace(c)) {
                spaces++;
            }
        }
        
        double validRatio = (double) validChars / totalChars;
        double spaceRatio = (double) spaces / totalChars;
        
        // Good quality text should have high valid chars and reasonable spacing
        return (validRatio * 0.7) + (Math.min(spaceRatio, 0.2) * 1.5);
    }
}
