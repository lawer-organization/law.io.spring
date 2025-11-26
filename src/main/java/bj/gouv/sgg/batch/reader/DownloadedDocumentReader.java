package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.FetchResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchResultRepository;
import bj.gouv.sgg.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

/**
 * Reader qui lit les documents téléchargés (status=DOWNLOADED)
 * et qui n'ont pas encore été extraits (pas de fichier OCR).
 * Respecte la limite maxDocumentsToExtract configurée.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.batch.core.configuration.annotation.StepScope
public class DownloadedDocumentReader implements ItemReader<LawDocument> {
    
    private final FetchResultRepository fetchResultRepository;
    private final FileStorageService fileStorageService;
    private final LawProperties properties;
    private Iterator<FetchResult> iterator;
    
    @Override
    public synchronized LawDocument read() {
        if (iterator == null) {
            initialize();
        }
        
        if (iterator.hasNext()) {
            FetchResult fetchResult = iterator.next();
            return convertToLawDocument(fetchResult);
        }
        
        return null;
    }
    
    private synchronized void initialize() {
        // Récupérer tous les documents téléchargés avec succès (status=DOWNLOADED)
        List<FetchResult> downloadedDocuments = fetchResultRepository
            .findByStatus("DOWNLOADED");
        
        log.info("Found {} documents with DOWNLOADED status in database", downloadedDocuments.size());
        
        // Filtrer ceux qui n'ont pas encore de fichier OCR
        List<FetchResult> toExtract = downloadedDocuments.stream()
            .filter(fetch -> !fileStorageService.ocrExists(fetch.getDocumentType(), fetch.getDocumentId()))
            // Trier du plus récent au plus ancien: year DESC, number DESC
            .sorted((a, b) -> {
                int yearCompare = Integer.compare(b.getYear(), a.getYear());
                if (yearCompare != 0) return yearCompare;
                return Integer.compare(b.getNumber(), a.getNumber());
            })
            .limit(properties.getBatch().getMaxDocumentsToExtract())
            .toList();
        
        log.info("Selected {} documents ready for OCR extraction (DOWNLOADED but no OCR file yet), limited to max {}", 
            toExtract.size(), properties.getBatch().getMaxDocumentsToExtract());
        
        iterator = toExtract.iterator();
    }
    
    private LawDocument convertToLawDocument(FetchResult fetchResult) {
        return LawDocument.builder()
            .type(fetchResult.getDocumentType())
            .year(fetchResult.getYear())
            .number(fetchResult.getNumber())
            .url(fetchResult.getUrl())
            .exists(true)
            .status(LawDocument.ProcessingStatus.DOWNLOADED)
            .build();
    }
    
    public void reset() {
        iterator = null;
    }
}
