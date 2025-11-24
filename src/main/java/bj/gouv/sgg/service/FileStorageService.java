package bj.gouv.sgg.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Service de stockage filesystem pour PDFs et OCR.
 * Structure:
 *   {LAW_DIRECTORIES_DATA}/pdfs/{type}/{documentId}.pdf
 *   {LAW_DIRECTORIES_DATA}/ocr/{type}/{documentId}.txt
 */
@Service
@Slf4j
public class FileStorageService {

    @Value("${law.directories.data:data}")
    private String baseDataDir;

    public Path pdfPath(String type, String documentId) {
        return Path.of(baseDataDir, "pdfs", type, documentId + ".pdf");
    }

    public Path ocrPath(String type, String documentId) {
        return Path.of(baseDataDir, "ocr", type, documentId + ".txt");
    }

    public boolean pdfExists(String type, String documentId) {
        return Files.exists(pdfPath(type, documentId));
    }

    public boolean ocrExists(String type, String documentId) {
        return Files.exists(ocrPath(type, documentId));
    }

    public void savePdf(String type, String documentId, byte[] content) throws IOException {
        Path path = pdfPath(type, documentId);
        ensureDir(path.getParent());
        Files.write(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.info("PDF sauvegardé: {} ({} octets)", path, content.length);
    }

    public byte[] readPdf(String type, String documentId) throws IOException {
        Path path = pdfPath(type, documentId);
        return Files.readAllBytes(path);
    }

    public void saveOcr(String type, String documentId, String text) throws IOException {
        Path path = ocrPath(type, documentId);
        ensureDir(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.info("OCR sauvegardé: {} ({} chars)", path, text.length());
    }

    public String readOcr(String type, String documentId) throws IOException {
        Path path = ocrPath(type, documentId);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public String getPdfPath(String type, String documentId) {
        return pdfPath(type, documentId).toString();
    }

    public String getOcrPath(String type, String documentId) {
        return ocrPath(type, documentId).toString();
    }

    private void ensureDir(Path dir) throws IOException {
        if (dir != null && !Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }
}
