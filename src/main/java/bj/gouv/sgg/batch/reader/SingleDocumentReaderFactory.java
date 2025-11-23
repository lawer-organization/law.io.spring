package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

/**
 * Factory qui crée un reader approprié selon les job parameters
 * Si documentId est spécifié, utilise SingleDocumentReader
 * Sinon délègue à un reader par défaut
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SingleDocumentReaderFactory implements ItemReader<LawDocument> {
    
    private final LawProperties properties;
    private final LawDocumentReader defaultReader;
    private ItemReader<LawDocument> actualReader;
    
    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        String documentId = stepExecution.getJobParameters().getString("documentId");
        
        if (documentId != null && !documentId.isEmpty()) {
            String type = stepExecution.getJobParameters().getString("type");
            Long year = stepExecution.getJobParameters().getLong("year");
            Long number = stepExecution.getJobParameters().getLong("number");
            
            log.info("Creating SingleDocumentReader for {}-{}-{}", type, year, number);
            actualReader = new SingleDocumentReader(properties, type, year.intValue(), number.intValue());
        } else {
            log.info("Using default LawDocumentReader");
            actualReader = defaultReader;
        }
    }
    
    @Override
    public LawDocument read() throws Exception {
        if (actualReader == null) {
            throw new IllegalStateException("Reader not initialized. BeforeStep should have been called.");
        }
        return actualReader.read();
    }
}
