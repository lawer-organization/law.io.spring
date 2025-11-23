package bj.gouv.sgg.service;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.Article;
import bj.gouv.sgg.model.ArticleExtraction;
import bj.gouv.sgg.model.DocumentMetadata;
import bj.gouv.sgg.model.FetchResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.ArticleExtractionRepository;
import bj.gouv.sgg.repository.FetchResultRepository;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service pour le traitement intelligent et idempotent d'un document spécifique
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final FetchResultRepository fetchResultRepository;
    private final ArticleExtractionRepository articleExtractionRepository;
    private final LawFetchService fetchService;
    private final PdfDownloadService downloadService;
    private final TesseractOcrService ocrService;
    private final ArticleExtractorService extractorService;
    private final FileStorageService fileStorageService;
    private final LawProperties properties;
    private final Gson gson;

    /**
     * Traite un document de manière intelligente et idempotente
     * 
     * @param documentId ID du document (ex: loi-2025-8, decret-2024-15)
     * @param force true pour forcer le retraitement même si déjà traité
     * @return Résultat du traitement
     */
    @Transactional
    public ProcessingResult processDocument(String documentId, boolean force) {
        log.info("Starting processing for document: {} (force={})", documentId, force);
        
        ProcessingResult result = new ProcessingResult();
        result.setDocumentId(documentId);
        result.setStartTime(LocalDateTime.now());
        result.setForce(force);

        try {
            // Parser le documentId pour extraire type, année, numéro
            DocumentInfo docInfo = parseDocumentId(documentId);
            if (docInfo == null) {
                result.setSuccess(false);
                result.setMessage("Invalid document ID format. Expected: loi-YYYY-NN or decret-YYYY-NN");
                return result;
            }

            // Étape 1: Vérifier si déjà traité (sauf en mode force)
            if (!force && isAlreadyProcessed(documentId)) {
                result.setSuccess(true);
                result.setMessage("Document already processed (use force=true to reprocess)");
                result.setSkipped(true);
                return result;
            }

            // Étape 2: Fetch (vérifier URL)
            log.info("Step 1/4: Fetching URL for {}", documentId);
            FetchResult fetchResult = fetchDocument(docInfo, force);
            if (fetchResult == null || "NOT_FOUND".equals(fetchResult.getStatus())) {
                result.setSuccess(false);
                result.setMessage("Document not found (404) on server");
                return result;
            }
            result.setFetched(true);

            // Étape 3: Download PDF
            log.info("Step 2/4: Downloading PDF for {}", documentId);
            File pdfFile = downloadPdf(fetchResult, force);
            if (pdfFile == null || !pdfFile.exists()) {
                result.setSuccess(false);
                result.setMessage("Failed to download PDF");
                return result;
            }
            result.setDownloaded(true);
            result.setPdfSize(pdfFile.length());

            // Étape 4: OCR
            log.info("Step 3/4: Performing OCR for {}", documentId);
            File ocrFile = performOcr(fetchResult, pdfFile, force);
            if (ocrFile == null || !ocrFile.exists()) {
                result.setSuccess(false);
                result.setMessage("Failed to perform OCR");
                return result;
            }
            result.setOcrProcessed(true);

            // Étape 5: Extraction et consolidation
            log.info("Step 4/4: Extracting articles for {}", documentId);
            ExtractionResult extractionResult = extractArticles(fetchResult, ocrFile, force);
            if (!extractionResult.isSuccess()) {
                result.setSuccess(false);
                result.setMessage("Failed to extract articles: " + extractionResult.getMessage());
                return result;
            }
            result.setExtracted(true);
            result.setArticleCount(extractionResult.getArticleCount());
            result.setConfidence(extractionResult.getConfidence());

            result.setSuccess(true);
            result.setMessage(String.format("Successfully processed %d articles (confidence: %.2f%%)", 
                extractionResult.getArticleCount(), extractionResult.getConfidence() * 100));

        } catch (Exception e) {
            log.error("Error processing document {}: {}", documentId, e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("Error: " + e.getMessage());
        } finally {
            result.setEndTime(LocalDateTime.now());
        }

        return result;
    }

    private boolean isAlreadyProcessed(String documentId) {
        // Vérifier si des articles existent déjà en base
        List<ArticleExtraction> articles = articleExtractionRepository.findByDocumentId(documentId);
        if (!articles.isEmpty()) {
            log.info("Document {} already has {} articles in database", documentId, articles.size());
            return true;
        }

        // Vérifier si le fichier JSON existe
        String[] parts = documentId.split("-");
        if (parts.length >= 2) {
            String type = parts[0];
            String jsonPath = properties.getDirectories().getData() + File.separator + "articles" + File.separator + type + File.separator + documentId + ".json";
            File jsonFile = new File(jsonPath);
            if (jsonFile.exists()) {
                log.info("Document {} already has JSON file", documentId);
                return true;
            }
        }

        return false;
    }

    private FetchResult fetchDocument(DocumentInfo docInfo, boolean force) {
        // Vérifier si déjà fetch (sauf en mode force)
        if (!force) {
            Optional<FetchResult> existing = fetchResultRepository.findByDocumentId(docInfo.documentId);
            if (existing.isPresent() && "DOWNLOADED".equals(existing.get().getStatus())) {
                log.info("Document {} already fetched", docInfo.documentId);
                return existing.get();
            }
        }

        // Fetch l'URL
        return fetchService.fetchSingleDocument(docInfo.type, docInfo.year, docInfo.number);
    }

    private File downloadPdf(FetchResult fetchResult, boolean force) throws Exception {
        String pdfPath = fileStorageService.getPdfPath(fetchResult.getDocumentType(), fetchResult.getDocumentId());
        File pdfFile = new File(pdfPath);

        // Si existe et non-force, retourner
        if (!force && pdfFile.exists() && pdfFile.length() > 0) {
            log.info("PDF already exists: {}", pdfPath);
            return pdfFile;
        }

        // Télécharger
        LawDocument doc = LawDocument.builder()
                .type(fetchResult.getDocumentType())
                .year(fetchResult.getYear())
                .number(fetchResult.getNumber())
                .url(fetchResult.getUrl())
                .status(LawDocument.ProcessingStatus.PENDING)
                .build();

        return downloadService.downloadPdf(doc);
    }

    private File performOcr(FetchResult fetchResult, File pdfFile, boolean force) throws Exception {
        String ocrPath = fileStorageService.getOcrPath(fetchResult.getDocumentType(), fetchResult.getDocumentId());
        File ocrFile = new File(ocrPath);

        // Si existe et non-force, retourner
        if (!force && ocrFile.exists() && ocrFile.length() > 0) {
            log.info("OCR file already exists: {}", ocrPath);
            return ocrFile;
        }

        // Effectuer OCR
        ocrService.performOcr(pdfFile, ocrFile);
        return ocrFile;
    }

    private ExtractionResult extractArticles(FetchResult fetchResult, File ocrFile, boolean force) throws Exception {
        ExtractionResult result = new ExtractionResult();

        try {
            // Si force, supprimer les anciens articles
            if (force) {
                articleExtractionRepository.deleteByDocumentId(fetchResult.getDocumentId());
                log.info("Deleted existing articles for {}", fetchResult.getDocumentId());
            }

            // Lire le fichier OCR
            String ocrText = Files.readString(ocrFile.toPath());
            
            // Extraire les articles avec le service
            List<Article> articles = extractorService.extractArticles(ocrText);
            DocumentMetadata metadata = extractorService.extractMetadata(ocrText);
            double confidence = extractorService.calculateConfidence(ocrText, articles);
            
            if (articles.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("No articles extracted");
                return result;
            }

            // Créer les entités ArticleExtraction
            List<ArticleExtraction> extractions = new ArrayList<>();
            for (Article article : articles) {
                ArticleExtraction extraction = ArticleExtraction.builder()
                        .documentId(fetchResult.getDocumentId())
                        .articleIndex(article.getIndex())
                        .title(String.format("%s article-%d", fetchResult.getDocumentId(), article.getIndex()))
                        .content(article.getContent())
                        .confidence(confidence)
                        .documentType(fetchResult.getDocumentType())
                        .documentYear(fetchResult.getYear())
                        .documentNumber(fetchResult.getNumber())
                        .sourceUrl(fetchResult.getUrl())
                        .promulgationDate(metadata.getPromulgationDate())
                        .signatories(metadata.getSignatories())
                        .extractedAt(LocalDateTime.now())
                        .build();
                extractions.add(extraction);
            }

            // Sauvegarder en JSON
            saveToJson(fetchResult, extractions);

            // Consolider en base de données
            articleExtractionRepository.saveAll(extractions);
            log.info("Consolidated {} articles for {} into database", extractions.size(), fetchResult.getDocumentId());
            
            result.setSuccess(true);
            result.setArticleCount(extractions.size());
            result.setConfidence(confidence);

        } catch (Exception e) {
            log.error("Error extracting articles: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage(e.getMessage());
        }

        return result;
    }

    private void saveToJson(FetchResult fetchResult, List<ArticleExtraction> extractions) throws Exception {
        String articlesPath = properties.getDirectories().getData() + File.separator + "articles" + File.separator + fetchResult.getDocumentType();
        File articlesDir = new File(articlesPath);
        if (!articlesDir.exists()) {
            articlesDir.mkdirs();
        }

        File jsonFile = new File(articlesDir, fetchResult.getDocumentId() + ".json");
        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(extractions, writer);
            log.info("Saved {} articles to JSON: {}", extractions.size(), jsonFile.getAbsolutePath());
        }
    }

    private DocumentInfo parseDocumentId(String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            return null;
        }

        String[] parts = documentId.split("-");
        if (parts.length < 3) {
            return null;
        }

        try {
            DocumentInfo info = new DocumentInfo();
            info.documentId = documentId;
            info.type = parts[0]; // loi ou decret
            info.year = Integer.parseInt(parts[1]);
            info.number = Integer.parseInt(parts[2]);

            if (!info.type.equals("loi") && !info.type.equals("decret")) {
                return null;
            }

            return info;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class DocumentInfo {
        String documentId;
        String type;
        int year;
        int number;
    }

    @lombok.Data
    public static class ProcessingResult {
        private String documentId;
        private boolean success;
        private String message;
        private boolean skipped;
        private boolean force;
        
        private boolean fetched;
        private boolean downloaded;
        private boolean ocrProcessed;
        private boolean extracted;
        
        private long pdfSize;
        private int articleCount;
        private double confidence;
        
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    @lombok.Data
    private static class ExtractionResult {
        private boolean success;
        private String message;
        private int articleCount;
        private double confidence;
    }
}
