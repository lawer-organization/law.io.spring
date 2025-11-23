package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.batch.util.LawDocumentFactory;
import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.FetchCursor;
import bj.gouv.sgg.model.LawDocument;
import bj.gouv.sgg.repository.FetchCursorRepository;
import bj.gouv.sgg.repository.FetchResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reader pour les années précédentes (1960 à année-1)
 * Utilise le cache BD pour éviter les URLs déjà vérifiées
 */
@Slf4j
@Component
public class PreviousYearsLawDocumentReader implements ItemReader<LawDocument> {
    
    private static final String CURSOR_TYPE = "fetch-previous";
    
    private final LawProperties properties;
    private final FetchResultRepository fetchResultRepository;
    private final FetchCursorRepository fetchCursorRepository;
    private final LawDocumentFactory documentFactory;
    private List<LawDocument> documents;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    
    public PreviousYearsLawDocumentReader(LawProperties properties, 
                                          FetchResultRepository fetchResultRepository,
                                          FetchCursorRepository fetchCursorRepository,
                                          LawDocumentFactory documentFactory) {
        this.properties = properties;
        this.fetchResultRepository = fetchResultRepository;
        this.fetchCursorRepository = fetchCursorRepository;
        this.documentFactory = documentFactory;
    }
    
    @Override
    public synchronized LawDocument read() {
        if (documents == null) {
            documents = generateDocuments();
            log.info("Generated {} documents for previous years (optimized scan)", documents.size());
        }
        
        int index = currentIndex.getAndIncrement();
        if (index < documents.size()) {
            return documents.get(index);
        }
        
        return null; // End of data
    }
    
    private List<LawDocument> generateDocuments() {
        List<LawDocument> docs = new ArrayList<>();
        
        // Charger TOUS les documentIds déjà en BD (found + not_found)
        Set<String> verifiedDocuments = new HashSet<>(fetchResultRepository.findAllDocumentIds());
        
        log.info("Loaded {} documents already verified in DB (will skip these)", verifiedDocuments.size());
        
        // Charger le cursor (dernière position)
        int[] cursor = loadCursor();
        int startYear = cursor[0];
        int startNumber = cursor[1];
        
        log.info("Cursor position: year={}, number={}", startYear, startNumber);
        log.info("Scanning from year {} number {} down to year {} (max {} items)", 
            startYear, startNumber, properties.getEndYear(), properties.getBatch().getMaxItemsToFetchPrevious());
        
        // Générer les documents et récupérer la dernière position
        int[] result = scanDocuments(docs, verifiedDocuments, startYear, startNumber);
        int lastYear = result[0];
        int lastNumber = result[1];
        int skippedCount = result[2];
        
        // Sauvegarder le nouveau cursor (position suivante)
        saveNextCursorPosition(lastYear, lastNumber);
        
        log.info("Skipped {} already verified documents (found + not_found)", skippedCount);
        
        // Afficher la plage d'URLs surveillée
        if (!docs.isEmpty()) {
            LawDocument first = docs.get(0);
            LawDocument last = docs.get(docs.size() - 1);
            log.info("URL range monitored: {} (loi-{}-{}) → {} (loi-{}-{})", 
                first.getUrl(), first.getYear(), first.getNumber(),
                last.getUrl(), last.getYear(), last.getNumber());
        }
        
        return docs;
    }
    
    public void reset() {
        currentIndex.set(0);
        documents = null;
    }
    
    /**
     * Scan et génère les documents à partir du cursor
     * @return [lastYear, lastNumber, skippedCount]
     */
    private int[] scanDocuments(List<LawDocument> docs, Set<String> verifiedDocuments, int startYear, int startNumber) {
        int maxItems = properties.getBatch().getMaxItemsToFetchPrevious();
        int lastYear = startYear;
        int lastNumber = startNumber;
        int skippedCount = 0;
        boolean limitReached = false;
        
        for (int year = startYear; year >= properties.getEndYear() && !limitReached; year--) {
            int startNum = (year == startYear) ? startNumber : properties.getMaxNumberPerYear();
            
            for (int number = startNum; number >= 1; number--) {
                lastYear = year;
                lastNumber = number;
                
                String loiDocId = String.format("loi-%d-%d", year, number);
                if (!verifiedDocuments.contains(loiDocId)) {
                    docs.add(documentFactory.create("loi", year, number));
                    
                    if (docs.size() >= maxItems) {
                        log.info("Reached max items limit: {}", maxItems);
                        limitReached = true;
                        break;
                    }
                } else {
                    skippedCount++;
                }
            }
        }
        
        return new int[]{lastYear, lastNumber, skippedCount};
    }
    
    /**
     * Calcule et sauvegarde la position suivante du cursor
     */
    private void saveNextCursorPosition(int lastYear, int lastNumber) {
        int nextYear = lastYear;
        int nextNumber = lastNumber - 1;
        if (nextNumber < 1) {
            nextYear = lastYear - 1;
            nextNumber = properties.getMaxNumberPerYear();
        }
        saveCursor(nextYear, nextNumber);
    }
    
    /**
     * Charge le cursor depuis la BD
     * @return [year, number] - position de départ
     */
    private int[] loadCursor() {
        try {
            FetchCursor cursor = fetchCursorRepository
                .findByCursorTypeAndDocumentType(CURSOR_TYPE, "loi")
                .orElse(null);
            if (cursor != null) {
                log.info("Loaded cursor from DB: year={}, number={}", cursor.getCurrentYear(), cursor.getCurrentNumber());
                return new int[]{cursor.getCurrentYear(), cursor.getCurrentNumber()};
            }
        } catch (Exception e) {
            log.warn("Could not load cursor from DB, starting from beginning: {}", e.getMessage());
        }
        
        // Par défaut: démarrer de l'année courante - 1
        int startYear = Calendar.getInstance().get(Calendar.YEAR) - 1;
        log.info("No cursor found, starting from year={}, number={}", startYear, properties.getMaxNumberPerYear());
        return new int[]{startYear, properties.getMaxNumberPerYear()};
    }
    
    /**
     * Sauvegarde le cursor dans la BD
     * @param year année courante
     * @param number numéro courant
     */
    private void saveCursor(int year, int number) {
        try {
            // Chercher le cursor existant ou créer un nouveau
            FetchCursor cursor = fetchCursorRepository
                .findByCursorTypeAndDocumentType(CURSOR_TYPE, "loi")
                .orElse(FetchCursor.builder()
                    .cursorType(CURSOR_TYPE)
                    .documentType("loi")
                    .build());
            
            cursor.setCurrentYear(year);
            cursor.setCurrentNumber(number);
            cursor.setUpdatedAt(LocalDateTime.now());
            
            fetchCursorRepository.save(cursor);
            log.debug("Saved cursor to DB: year={}, number={}", year, number);
        } catch (Exception e) {
            log.error("Could not save cursor to DB: {}", e.getMessage());
        }
    }
}
