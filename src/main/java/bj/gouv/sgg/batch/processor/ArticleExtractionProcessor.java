package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.*;
import bj.gouv.sgg.service.ArticleExtractorService;
import bj.gouv.sgg.service.FileStorageService;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Processor qui extrait les articles depuis un fichier OCR et les exporte en JSON sur disque
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleExtractionProcessor implements ItemProcessor<LawDocument, LawDocument> {

    private final LawProperties properties;
    private final ArticleExtractorService extractorService;
    private final FileStorageService fileStorageService;
    private final Gson gson;

    @Override
    public LawDocument process(LawDocument document) throws Exception {
        // Vérifier si le fichier JSON existe déjà
        String articlesPath = properties.getDirectories().getData() + File.separator + "articles" + File.separator + document.getType();
        File articlesDir = new File(articlesPath);
        File jsonFile = new File(articlesDir, document.getDocumentId() + ".json");
        
        if (jsonFile.exists()) {
            log.debug("Articles JSON already exists: {}", document.getDocumentId());
            document.setStatus(LawDocument.ProcessingStatus.EXTRACTED);
            return document;
        }

        // Lire le fichier OCR
        String ocrText;
        try {
            ocrText = fileStorageService.readOcr(document.getType(), document.getDocumentId());
            log.debug("Loaded OCR text ({} chars) for {}", ocrText.length(), document.getDocumentId());
        } catch (Exception e) {
            log.warn("Failed to read OCR file for {}: {}", document.getDocumentId(), e.getMessage());
            return null;
        }

        if (ocrText.trim().isEmpty() || ocrText.length() < 100) {
            log.warn("Insufficient OCR text for: {}", document.getDocumentId());
            return null;
        }

        // Extraire les articles
        List<Article> articles = extractorService.extractArticles(ocrText);
        DocumentMetadata metadata = extractorService.extractMetadata(ocrText);
        double confidence = extractorService.calculateConfidence(ocrText, articles);
        log.info("Extracted {} articles from: {} (confidence: {:.2f})", articles.size(), document.getDocumentId(), confidence);

        // Préparer les données d'extraction pour export JSON
        List<ArticleExtraction> extractions = new ArrayList<>();
        for (Article article : articles) {
            ArticleExtraction extraction = ArticleExtraction.builder()
                    .documentId(document.getDocumentId())
                    .articleIndex(article.getIndex())
                    .title(String.format("%s article-%d", document.getDocumentId(), article.getIndex()))
                    .content(article.getContent())
                    .confidence(confidence)
                    .documentType(document.getType())
                    .documentYear(document.getYear())
                    .documentNumber(document.getNumber())
                    .sourceUrl(String.format("%s/%s/download", properties.getBaseUrl(), document.getDocumentId()))
                    .lawTitle(metadata.getLawTitle())
                    .promulgationDate(metadata.getPromulgationDate())
                    .promulgationCity(metadata.getPromulgationCity())
                    .signatories(metadata.getSignatories())
                    .extractedAt(LocalDateTime.now())
                    .build();
            extractions.add(extraction);
        }

        // Exporter en JSON sur disque
        if (!articlesDir.exists()) {
            articlesDir.mkdirs();
        }
        
        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(extractions, writer);
            log.info("Exported {} articles to JSON file: {}", extractions.size(), jsonFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to write JSON file for {}: {}", document.getDocumentId(), e.getMessage());
            throw e;
        }

        document.setStatus(LawDocument.ProcessingStatus.EXTRACTED);
        return document;
    }
}
