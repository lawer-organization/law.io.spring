package bj.gouv.sgg.batch.processor;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.*;
import bj.gouv.sgg.repository.ArticleExtractionRepository;
import bj.gouv.sgg.service.ArticleExtractorService;
import bj.gouv.sgg.service.FileStorageService;
import bj.gouv.sgg.service.TesseractOcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Processor qui extrait les articles depuis un PDF stocké sur disque
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractionProcessor implements ItemProcessor<LawDocument, LawDocument> {

    private final LawProperties properties;
    private final TesseractOcrService ocrService;
    private final ArticleExtractorService extractorService;
    private final ArticleExtractionRepository articleExtractionRepository;
    private final FileStorageService fileStorageService;

    @Override
    public LawDocument process(LawDocument document) throws Exception {
        if (articleExtractionRepository.existsByDocumentId(document.getDocumentId())) {
            log.debug("Articles already extracted: {}", document.getDocumentId());
            document.setStatus(LawDocument.ProcessingStatus.EXTRACTED);
            return document;
        }

        // OCR
        String ocrText;
        if (fileStorageService.ocrExists(document.getType(), document.getDocumentId())) {
            ocrText = fileStorageService.readOcr(document.getType(), document.getDocumentId());
            log.debug("Loaded existing OCR ({}) for {}", ocrText.length(), document.getDocumentId());
        } else {
            byte[] pdfBytes = fileStorageService.readPdf(document.getType(), document.getDocumentId());
            ocrText = ocrService.extractText(pdfBytes);
            fileStorageService.saveOcr(document.getType(), document.getDocumentId(), ocrText);
            log.info("OCR extracted and saved: {} ({} chars)", document.getDocumentId(), ocrText.length());
        }

        if (ocrText.trim().isEmpty() || ocrText.length() < 1000) {
            log.warn("Insufficient text extracted from: {}", document.getDocumentId());
            return null;
        }

        List<Article> articles = extractorService.extractArticles(ocrText);
        DocumentMetadata metadata = extractorService.extractMetadata(ocrText);
        double confidence = extractorService.calculateConfidence(ocrText, articles);
        log.info("Extracted {} articles from: {} (confidence: {:.2f})", articles.size(), document.getDocumentId(), confidence);

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
        articleExtractionRepository.saveAll(extractions);
        log.info("Saved {} articles to database for: {}", extractions.size(), document.getDocumentId());

        document.setStatus(LawDocument.ProcessingStatus.EXTRACTED);
        return document;
    }
}
