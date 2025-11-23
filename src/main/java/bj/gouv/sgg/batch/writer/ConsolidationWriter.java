package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.model.ArticleExtraction;
import bj.gouv.sgg.model.FetchResult;
import bj.gouv.sgg.repository.ArticleExtractionRepository;
import bj.gouv.sgg.repository.FetchResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Writer qui consolide les articles extraits depuis JSON vers la base de données
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsolidationWriter implements ItemWriter<List<ArticleExtraction>> {

    private final ArticleExtractionRepository articleExtractionRepository;
    private final FetchResultRepository fetchResultRepository;

    @Override
    @Transactional
    public void write(Chunk<? extends List<ArticleExtraction>> chunk) throws Exception {
        int totalArticles = 0;
        int totalDocuments = 0;

        for (List<ArticleExtraction> extractions : chunk) {
            if (extractions == null || extractions.isEmpty()) {
                continue;
            }

            String documentId = extractions.get(0).getDocumentId();
            
            // Supprimer les anciennes extractions si elles existent
            articleExtractionRepository.deleteByDocumentId(documentId);
            
            // Sauvegarder les nouvelles extractions
            articleExtractionRepository.saveAll(extractions);
            totalArticles += extractions.size();
            totalDocuments++;

            // Mettre à jour le statut dans fetch_results
            Optional<FetchResult> fetchResultOpt = fetchResultRepository.findByDocumentId(documentId);
            if (fetchResultOpt.isPresent()) {
                FetchResult fetchResult = fetchResultOpt.get();
                fetchResult.setStatus("CONSOLIDATED");
                fetchResultRepository.save(fetchResult);
            }

            log.info("Consolidated {} articles for: {}", extractions.size(), documentId);
        }

        log.info("Consolidation complete: {} articles from {} documents", totalArticles, totalDocuments);
    }
}
