package bj.gouv.sgg.service;

import bj.gouv.sgg.exception.EmptyPdfException;
import bj.gouv.sgg.exception.PdfDownloadException;
import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;

/**
 * Service pour le téléchargement de PDF
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfDownloadService {

    private final RestTemplate restTemplate;
    private final FileStorageService fileStorageService;

    public File downloadPdf(LawDocument document) {
        String url = document.getUrl() + "/download";
        String pdfPath = fileStorageService.getPdfPath(document.getType(), document.getDocumentId());
        
        File pdfFile = new File(pdfPath);
        File parentDir = pdfFile.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        log.info("Downloading PDF from: {}", url);

        try {
            byte[] pdfBytes = restTemplate.getForObject(URI.create(url), byte[].class);
            
            if (pdfBytes == null || pdfBytes.length == 0) {
                throw new EmptyPdfException(document.getDocumentId(), url);
            }

            try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                fos.write(pdfBytes);
            }

            log.info("PDF downloaded successfully: {} ({} bytes)", pdfFile.getAbsolutePath(), pdfBytes.length);
            return pdfFile;

        } catch (EmptyPdfException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to download PDF: {}", e.getMessage());
            throw new PdfDownloadException(document.getDocumentId(), url, e);
        }
    }
}
