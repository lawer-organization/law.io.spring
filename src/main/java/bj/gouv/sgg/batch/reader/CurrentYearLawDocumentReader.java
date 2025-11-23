package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.batch.util.LawDocumentFactory;
import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reader pour l'année courante.
 * Parcourt systématiquement toutes les combinaisons (1..maxNumberPerYear) pour l'année courante
 * en excluant uniquement les documents déjà TROUVÉS (présents dans fetch_results).
 * Les numéros NOT_FOUND sont donc retestés à chaque exécution pour détecter l'apparition tardive.
 */
@Slf4j
@Component
public class CurrentYearLawDocumentReader implements ItemReader<LawDocument> {
    
    private final LawProperties properties;
    private final FetchResultRepository fetchResultRepository;
    private final LawDocumentFactory documentFactory;
    private List<LawDocument> documents;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    
    public CurrentYearLawDocumentReader(LawProperties properties,
                                       FetchResultRepository fetchResultRepository,
                                       LawDocumentFactory documentFactory) {
        this.properties = properties;
        this.fetchResultRepository = fetchResultRepository;
        this.documentFactory = documentFactory;
    }
    
    @Override
    public synchronized LawDocument read() {
        if (documents == null) {
            documents = generateDocuments();
            log.info("Generated {} documents for current year (full scan, no cache)", documents.size());
        }
        
        int index = currentIndex.getAndIncrement();
        if (index < documents.size()) {
            return documents.get(index);
        }
        
        return null; // End of data
    }
    
    private List<LawDocument> generateDocuments() {
        List<LawDocument> docs = new ArrayList<>();

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int maxNumber = properties.getMaxNumberPerYear();

        // Charger uniquement les documentIds TROUVÉS (FetchResult) pour exclusion
        Set<String> foundDocuments = new HashSet<>(fetchResultRepository.findAllDocumentIds());
        log.info("Loaded {} FOUND documents (will skip these)", foundDocuments.size());

        for (int number = 1; number <= maxNumber; number++) {
            String loiDocId = String.format("loi-%d-%d", currentYear, number);
            if (!foundDocuments.contains(loiDocId)) {
                docs.add(documentFactory.create("loi", currentYear, number));
            }
        }

        log.info("Generated {} candidate documents for current year {} ({} were already FOUND)", 
            docs.size(), currentYear, foundDocuments.size());

        if (!docs.isEmpty()) {
            LawDocument first = docs.get(0);
            LawDocument last = docs.get(docs.size() - 1);
            log.info("Scan range: {} → {}", first.getUrl(), last.getUrl());
        }

        return docs;
    }
    
    /**
     * Charge le cursor depuis la BD
     * @return [year, number] - position de départ
     */
    // Cursor logic removed: current-year scan now ignores persisted cursor
    
    public void reset() {
        currentIndex.set(0);
        documents = null;
    }
}
