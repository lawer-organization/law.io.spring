package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reader pour traiter un document unique spécifié par son type, année et numéro
 */
@Slf4j
public class SingleDocumentReader implements ItemReader<LawDocument> {
    
    private final LawProperties properties;
    private final String type;
    private final int year;
    private final int number;
    private final AtomicBoolean read = new AtomicBoolean(false);
    
    public SingleDocumentReader(LawProperties properties, String type, int year, int number) {
        this.properties = properties;
        this.type = type;
        this.year = year;
        this.number = number;
    }
    
    @Override
    public LawDocument read() {
        if (read.compareAndSet(false, true)) {
            log.info("Reading single document: {}-{}-{}", type, year, number);
            return createDocument();
        }
        return null; // End of data after single read
    }
    
    private LawDocument createDocument() {
        // Générer l'URL sans padding initialement (ex: loi-2025-1)
        String url = String.format("%s/%s-%d-%d/download", 
            properties.getBaseUrl(), type, year, number);
        
        return LawDocument.builder()
            .type(type)
            .year(year)
            .number(number)
            .url(url)
            .status(LawDocument.ProcessingStatus.PENDING)
            .build();
    }
}
