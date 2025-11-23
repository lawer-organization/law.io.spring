package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Writer qui met à jour le statut des documents après extraction d'articles
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleExtractionWriter implements ItemWriter<LawDocument> {

    private final FetchResultRepository fetchResultRepository;

    @Override
    public void write(Chunk<? extends LawDocument> chunk) {
        int processed = 0;
        int totalArticles = 0;

        for (LawDocument document : chunk) {
            if (document != null && document.getStatus() == LawDocument.ProcessingStatus.EXTRACTED) {
                // Mettre à jour le statut dans fetch_results
                fetchResultRepository.findByDocumentId(document.getDocumentId())
                        .ifPresent(fetchResult -> {
                            fetchResult.setStatus("EXTRACTED");
                            fetchResultRepository.save(fetchResult);
                        });
                processed++;
            }
        }

        if (processed > 0) {
            log.info("Article extraction summary: {} documents processed, {} total articles extracted", 
                processed, totalArticles);
        }
    }
}
