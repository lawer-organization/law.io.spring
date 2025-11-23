package bj.gouv.sgg.batch.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Utilitaire pour parser les documentId (format: type-year-number)
 */
@Slf4j
public class DocumentIdParser {

    private DocumentIdParser() {
        // Utility class
    }

    /**
     * Parse un documentId et retourne les composants
     * @param documentId Format attendu: loi-2025-8 ou decret-2024-15
     * @return ParsedDocument ou null si invalide
     */
    public static ParsedDocument parse(String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            return null;
        }

        String[] parts = documentId.split("-");
        if (parts.length < 3) {
            log.warn("Invalid documentId format: {} (expected: type-year-number)", documentId);
            return null;
        }

        try {
            String type = parts[0];
            int year = Integer.parseInt(parts[1]);
            int number = Integer.parseInt(parts[2]);

            if (!type.equals("loi") && !type.equals("decret")) {
                log.warn("Invalid document type: {} (expected: loi or decret)", type);
                return null;
            }

            return new ParsedDocument(documentId, type, year, number);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse documentId: {} - {}", documentId, e.getMessage());
            return null;
        }
    }

    /**
     * Résultat du parsing d'un documentId
     */
    public static class ParsedDocument {
        private final String documentId;
        private final String type;
        private final int year;
        private final int number;

        public ParsedDocument(String documentId, String type, int year, int number) {
            this.documentId = documentId;
            this.type = type;
            this.year = year;
            this.number = number;
        }

        public String getDocumentId() {
            return documentId;
        }

        public String getType() {
            return type;
        }

        public int getYear() {
            return year;
        }

        public int getNumber() {
            return number;
        }
    }
}
