package bj.gouv.sgg.batch.reader;

import bj.gouv.sgg.batch.util.DocumentIdParser;
import bj.gouv.sgg.batch.util.LawDocumentFactory;
import bj.gouv.sgg.config.LawProperties;
import bj.gouv.sgg.model.LawDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader qui scanne les fichiers JSON d'articles à consolider en base de données
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsolidationReader implements ItemReader<LawDocument> {

    private final LawProperties properties;
    private final LawDocumentFactory documentFactory;
    private List<LawDocument> documents;
    private int currentIndex = 0;

    @Override
    public LawDocument read() throws Exception {
        if (documents == null) {
            documents = scanJsonFiles();
            log.info("Found {} JSON files to consolidate", documents.size());
        }

        if (currentIndex < documents.size()) {
            return documents.get(currentIndex++);
        }

        return null;
    }

    private List<LawDocument> scanJsonFiles() {
        List<LawDocument> result = new ArrayList<>();
        
        // Scanner les types de documents (loi, decret)
        String[] types = {"loi", "decret"};
        for (String type : types) {
            String articlesPath = properties.getDirectories().getData() + File.separator + "articles" + File.separator + type;
            File articlesDir = new File(articlesPath);
            
            if (!articlesDir.exists() || !articlesDir.isDirectory()) {
                log.debug("Articles directory not found: {}", articlesPath);
                continue;
            }

            File[] jsonFiles = articlesDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (jsonFiles != null) {
                for (File jsonFile : jsonFiles) {
                    String documentId = jsonFile.getName().replace(".json", "");
                    
                    // Parser le documentId avec l'utilitaire
                    DocumentIdParser.ParsedDocument parsed = DocumentIdParser.parse(documentId);
                    if (parsed != null) {
                        LawDocument doc = documentFactory.create(parsed.getType(), parsed.getYear(), parsed.getNumber());
                        doc.setStatus(LawDocument.ProcessingStatus.EXTRACTED);
                        result.add(doc);
                    }
                }
            }
        }

        log.info("Scanned {} JSON files for consolidation", result.size());
        return result;
    }
}
