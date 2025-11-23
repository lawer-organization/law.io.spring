package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.model.ArticleExtraction;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.ArticleExtractionRepository;
import bj.gouv.sgg.repository.FetchResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Writer spécialisé pour les extractions d'articles
 * Note: Les articles sont déjà sauvegardés par ExtractionProcessor
 * Ce writer met à jour le statut EXTRACTED dans fetch_results
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractionWriter implements ItemWriter<LawDocument> {
    
    private final ArticleExtractionRepository articleExtractionRepository;
    private final FetchResultRepository fetchResultRepository;
    
    @Override
    public void write(Chunk<? extends LawDocument> chunk) throws Exception {
        int totalArticles = 0;
        int documentsProcessed = 0;
        int statusUpdated = 0;
        
        for (LawDocument document : chunk) {
            if (document == null) continue;
            
            // Compter les articles extraits pour ce document
            List<ArticleExtraction> articles = articleExtractionRepository
                .findByDocumentIdOrderByArticleIndex(document.getDocumentId());
            
            if (!articles.isEmpty()) {
                totalArticles += articles.size();
                documentsProcessed++;
                log.debug("Processed extraction for: {} ({} articles)", 
                         document.getDocumentId(), articles.size());
                
                // Mettre à jour le statut dans fetch_results
                fetchResultRepository.findByDocumentId(document.getDocumentId())
                    .ifPresent(fetchResult -> {
                        fetchResult.setStatus("EXTRACTED");
                        fetchResultRepository.save(fetchResult);
                        log.debug("Updated status to EXTRACTED for: {}", document.getDocumentId());
                    });
                statusUpdated++;
            }
        }
        
        log.info("Extraction summary: {} documents processed, {} articles extracted, {} status updated", 
                 documentsProcessed, totalArticles, statusUpdated);
    }
}
