package bj.gouv.sgg.batch.reader;

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
 * Reader qui lit les documents fetchés avec succès (status=FETCHED, exists=true)
 * et qui n'ont pas encore été téléchargés
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.batch.core.configuration.annotation.StepScope
public class FetchedDocumentReader implements ItemReader<LawDocument> {
    
    private final FetchResultRepository fetchResultRepository;
    private final FileStorageService fileStorageService;
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
        // Récupérer tous les documents fetchés avec succès (status=FETCHED ou FOUND)
        List<FetchResult> fetchedDocuments = fetchResultRepository
            .findByStatus("FETCHED");
        
        // Filtrer ceux qui n'ont pas encore été téléchargés avec succès (OK ou DUPLICATE)
        // Conformément à la logique de CrawlerService qui exclut les statuses OK/DUPLICATE
        List<FetchResult> toDownload = fetchedDocuments.stream()
            .filter(fetch -> !fileStorageService.pdfExists(fetch.getDocumentType(), fetch.getDocumentId()))
            // Trier du plus récent au plus ancien: year DESC, number DESC
            // Conformément à CrawlerService.run() lignes 168-177
            .sorted((a, b) -> {
                int yearCompare = Integer.compare(b.getYear(), a.getYear());
                if (yearCompare != 0) return yearCompare;
                return Integer.compare(b.getNumber(), a.getNumber());
            })
            .toList();
        
        log.info("Found {} documents ready to download (fetched but not yet downloaded), sorted by year DESC, number DESC", 
            toDownload.size());
        
        iterator = toDownload.iterator();
    }
    
    private LawDocument convertToLawDocument(FetchResult fetchResult) {
        // Utiliser directement les champs de FetchResult au lieu de parser documentId
        return LawDocument.builder()
            .type(fetchResult.getDocumentType())
            .year(fetchResult.getYear())
            .number(fetchResult.getNumber())
            .url(fetchResult.getUrl())
            .exists(true)
            .status(LawDocument.ProcessingStatus.FETCHED)
            .build();
    }
}
