package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.model.FetchResult;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchResultRepository;
import bj.gouv.sgg.service.NotFoundRangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Writer spécialisé pour les fetch results
 * - Documents FOUND → fetch_results (INSERT-ONLY)
 * - Documents NOT_FOUND → fetch_not_found_ranges (consolidés en plages)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchWriter implements ItemWriter<LawDocument> {
    
    private final FetchResultRepository repository;
    private final NotFoundRangeService notFoundRangeService;
    
    @Override
    public synchronized void write(Chunk<? extends LawDocument> chunk) throws Exception {
        List<FetchResult> foundDocuments = new ArrayList<>();
        List<LawDocument> notFoundDocuments = new ArrayList<>();
        int newFoundCount = 0;
        int newNotFoundCount = 0;
        int skippedCount = 0;
        
        for (LawDocument document : chunk) {
            // Vérifier si déjà existant (INSERT-ONLY, pas d'UPDATE)
            if (repository.existsByDocumentId(document.getDocumentId())) {
                log.debug("Already fetched, skipping: {}", document.getDocumentId());
                skippedCount++;
                continue;
            }
            
            if (document.isExists()) {
                // Document FOUND → Sauvegarder dans fetch_results
                FetchResult result = FetchResult.builder()
                    .documentId(document.getDocumentId())
                    .documentType(document.getType())
                    .year(document.getYear())
                    .number(document.getNumber())
                    .url(document.getUrl())
                    .status(document.getStatus() != null ? document.getStatus().name() : "FOUND")
                    .fetchedAt(LocalDateTime.now())
                    .errorMessage(null)
                    .build();
                
                foundDocuments.add(result);
                newFoundCount++;
                log.debug("New FOUND document: {}", document.getDocumentId());
            } else {
                // Document NOT_FOUND → Ajouter aux plages
                notFoundDocuments.add(document);
                newNotFoundCount++;
                log.trace("Document NOT_FOUND: {}", document.getDocumentId());
            }
        }
        
        // Sauvegarde en batch des documents FOUND
        if (!foundDocuments.isEmpty()) {
            repository.saveAll(foundDocuments);
        }
        
        // Consolidation des plages NOT_FOUND (simple, sans consolidation globale post-chunk)
        if (!notFoundDocuments.isEmpty()) {
            notFoundRangeService.addNotFoundDocuments(notFoundDocuments);
        }
        
        // Log récapitulatif
        if (newFoundCount > 0 || newNotFoundCount > 0 || skippedCount > 0) {
            log.info("Saved {} FOUND, {} NOT_FOUND consolidated to ranges ({} skipped)", 
                newFoundCount, newNotFoundCount, skippedCount);
        }
    }
}
