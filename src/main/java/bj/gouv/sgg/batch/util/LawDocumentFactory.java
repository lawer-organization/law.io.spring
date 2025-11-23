package bj.gouv.sgg.batch.util;

import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Factory pour créer des LawDocument de manière cohérente
 */
@Component
@RequiredArgsConstructor
public class LawDocumentFactory {

    private final LawProperties properties;

    /**
     * Crée un LawDocument à partir de type, year, number
     * @param type "loi" ou "decret"
     * @param year année
     * @param number numéro
     * @return LawDocument configuré
     */
    public LawDocument create(String type, int year, int number) {
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

    /**
     * Crée un LawDocument à partir d'un documentId
     * @param documentId Format: type-year-number
     * @return LawDocument ou null si parsing échoue
     */
    public LawDocument createFromDocumentId(String documentId) {
        DocumentIdParser.ParsedDocument parsed = DocumentIdParser.parse(documentId);
        if (parsed == null) {
            return null;
        }
        return create(parsed.getType(), parsed.getYear(), parsed.getNumber());
    }
}
