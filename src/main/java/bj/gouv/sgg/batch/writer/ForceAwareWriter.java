package bj.gouv.sgg.batch.writer;

import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;


/**
 * Writer qui gère le mode force pour écraser les données existantes
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForceAwareWriter implements ItemWriter<LawDocument> {
    
    private final FetchWriter fetchWriter;
    private final FetchResultRepository fetchResultRepository;
    
    private boolean force = false;
    
    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        String forceParam = stepExecution.getJobParameters().getString("force");
        this.force = "true".equalsIgnoreCase(forceParam);
        
        if (force) {
            String documentId = stepExecution.getJobParameters().getString("documentId");
            if (documentId != null && !documentId.isEmpty()) {
                log.info("Force mode enabled for document: {}", documentId);
                deleteExistingData(documentId);
            }
        }
    }
    
    @Override
    public void write(Chunk<? extends LawDocument> chunk) throws Exception {
        fetchWriter.write(chunk);
    }
    
    private void deleteExistingData(String documentId) {
        log.info("Deleting existing data for document: {}", documentId);
        
        // Supprimer les données de fetch
        fetchResultRepository.findByDocumentId(documentId).ifPresent(result -> {
            fetchResultRepository.delete(result);
            log.info("Deleted existing fetch result for: {}", documentId);
        });
        // Note: Les fichiers PDF/OCR sur disque ne sont pas supprimés ici pour conserver un cache manuel.
    }
}
